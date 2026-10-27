package com.sammwy.retrocraft.server;

import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.sammwy.retrocraft.Retrocraft;
import com.sammwy.retrocraft.cores.*;
import com.sammwy.retrocraft.cores.ownership.Ownership;
import com.sammwy.retrocraft.cores.stream.*;
import com.sammwy.retrocraft.cores.transfer.*;
import com.sammwy.retrocraft.cores.worker.GameLinkBridge;
import com.sammwy.retrocraft.cores.worker.HardwareProfile;
import com.sammwy.retrocraft.cores.worker.PadInput;
import com.sammwy.retrocraft.cores.worker.WorkerProtocol;
import com.sammwy.retrocraft.platform.Network;
import com.sammwy.retrocraft.roms.*;
import com.sammwy.retrocraft.roms.discovery.ArcadeSets;
import com.sammwy.retrocraft.server.block.*;
import com.sammwy.retrocraft.server.config.ConfigKey;
import com.sammwy.retrocraft.server.config.RetrocraftConfig;
import com.sammwy.retrocraft.server.console.*;
import com.sammwy.retrocraft.server.console.component.Components;
import com.sammwy.retrocraft.server.item.*;
import com.sammwy.retrocraft.server.net.HybridPackets;
import com.sammwy.retrocraft.server.net.LinkPackets;
import com.sammwy.retrocraft.server.net.Packets;
import com.sammwy.retrocraft.server.net.SettingsPackets;
import com.sammwy.retrocraft.server.screen.*;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

/** Server-owned emulation. Video, audio and input are separate subscriptions. */
public final class InstanceManager implements AutoCloseable {
    private final MinecraftServer server;
    private volatile RetrocraftConfig config;
    private RomIndex roms;
    private boolean scanning;
    private final InstanceStore store;
    private final GameSaves saves;
    private Path workerJar;
    private CoreManager cores;
    private final Map<String, CoreManager.Prepared> preparedCores = new ConcurrentHashMap<>();
    private final Map<UUID, Packets.Preferences> preferences = new HashMap<>();
    private final Map<UUID, Integer> clientGenerations = new HashMap<>();
    private final Set<UUID> readyClients = new HashSet<>();
    private final Set<UUID> verifiedClients = new HashSet<>(), biosReadyClients = new HashSet<>();
    private final Map<UUID, ArrayDeque<BlobTransfer.Sender>> outbound = new HashMap<>();
    private final Set<UUID> biosManifestSent = new HashSet<>();
    private final Map<UUID, Set<String>> biosPending = new HashMap<>();
    private final Map<UUID, ArrayDeque<BiosOutgoing>> biosOutgoing = new HashMap<>();

    private static final class BiosOutgoing {
        final String core, name, sha;
        final byte[] bytes;
        int offset;

        BiosOutgoing(String core, String name, String sha, byte[] bytes) {
            this.core = core;
            this.name = name;
            this.sha = sha;
            this.bytes = bytes;
        }
    }

    private Set<String> requiredCores = Set.of();
    private final Set<String> preparingCores = new HashSet<>();
    private List<RomCatalog.Entry> publishedCatalog = List.of();
    private final RomLibrary romLibrary, biosLibrary;
    private final Map<UUID, Upload> romUploads = new HashMap<>();
    private final Downloads downloads = new Downloads();
    private long nextDownloadNotice;
    private final Set<UUID> downloadNotified = new HashSet<>();
    private final ThreadPoolExecutor libraryIo =
            new ThreadPoolExecutor(
                    1,
                    1,
                    0L,
                    TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(32),
                    task -> {
                        Thread thread = new Thread(task, "retrocraft-library-io");
                        thread.setDaemon(true);
                        return thread;
                    });
    private boolean pendingScan, pendingOnlyNew;
    private int coreGeneration;
    private final ConcurrentLinkedQueue<Runnable> callbacks = new ConcurrentLinkedQueue<>();
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Set<ItemEntity> droppedItems = new HashSet<>();
    private boolean itemsIndexed;

    private static final class PendingStart {
        volatile UUID created;
    }

    private final Map<Object, PendingStart> pendingStarts = new IdentityHashMap<>();
    private final Map<UUID, UUID> videoWatching = new HashMap<>(), inputSessions = new HashMap<>();
    private final Set<UUID> failedDiscovery = new HashSet<>();
    private int ticks, catalogGeneration;
    private volatile boolean stopping;

    private static final class VideoWatcher {
        long lastSeen = System.nanoTime(), lastSent, lastAcknowledged, lastResync;
        boolean needsKeyframe = true;

        /**
         * Attached by proximity to an in-world screen (television / arcade cabinet) instead of by
         * opening the play screen.
         */
        boolean ambient;
    }

    private static final class AudioWatcher {
        float gain;
        long lastSent, lastAcknowledged;

        AudioWatcher(float gain) {
            this.gain = gain;
        }
    }

    private static final class InputSource {
        final UUID player;
        volatile PadInput state = PadInput.IDLE;
        volatile long lastSeen = System.nanoTime();

        InputSource(UUID player) {
            this.player = player;
        }
    }

    private static final class Media {
        private FrameCodec.Frame frame;
        private ByteArrayOutputStream audio = new ByteArrayOutputStream();
        private boolean updated;

        synchronized void offer(WorkerProcess.Output output, int rate) {
            if (output.frame() != null) frame = output.frame();
            updated = true;
            int limit = Math.min(WorkerProtocol.MAX_AUDIO, rate); // at most 250ms stereo PCM
            if (audio.size() + output.audio().length > limit) audio.reset();
            if (output.audio().length <= limit) audio.writeBytes(output.audio());
        }

        synchronized WorkerProcess.Output drain() {
            if (!updated) return null;
            var result = new WorkerProcess.Output(frame, audio.toByteArray());
            frame = null;
            audio.reset();
            updated = false;
            return result;
        }

        synchronized void clear() {
            frame = null;
            audio = new ByteArrayOutputStream();
            updated = false;
        }
    }

    /** Blocks between two consoles joined by a link cable. */
    private static final double LINK_RANGE = 16;

    private final LinkInvitations linkInvitations = new LinkInvitations();
    private final Map<UUID, Long> lastLinkRequest = new HashMap<>();

    private static final class Session {
        final InstanceStore.Metadata data;
        volatile RomIndex.Game game;
        Boolean clientFit;
        boolean checkingFit;
        ConsoleHost host;
        final Map<UUID, VideoWatcher> videoWatchers = new HashMap<>();
        final Map<UUID, AudioWatcher> audioWatchers = new HashMap<>();
        final Media media = new Media();
        FrameCodec.Encoder encoder = new FrameCodec.Encoder();
        boolean encodingVideo;
        int videoGeneration;

        /**
         * Controller slots; only the first one is usable on handhelds. Slot 0 also holds the
         * emulation lease.
         */
        volatile PadInput[] inputMasks = new PadInput[WorkerProtocol.PORTS];

        final InputSource[] pads = new InputSource[WorkerProtocol.PORTS];
        volatile boolean wantsVideo, wantsAudio, observed, finished, destroy;
        volatile long invalidSince;
        boolean poweredOff;
        final AtomicBoolean cleanupStarted = new AtomicBoolean();
        volatile FrameCodec.Frame lastFrame;
        volatile boolean thumbDue;
        volatile FrameCodec.Frame lastThumbSource;
        volatile byte[] lastThumb;
        volatile long lastUploadAt;
        volatile WorkerProcess process;
        volatile Throwable failure;
        volatile int sampleRate;
        final Ownership ownership = new Ownership();
        volatile Map<BlobTransfer.Kind, BlobTransfer.Receiver> incoming = new ConcurrentHashMap<>();
        final ArrayDeque<UploadChunk> uploadQueue = new ArrayDeque<>();
        boolean decodingUpload;
        volatile long lastIncomingChunk;
        final Set<String> pendingCheckpointWrites = new HashSet<>();
        final AtomicInteger checkpointWrites = new AtomicInteger();
        FrameCodec.Decoder relayDecoder = new FrameCodec.Decoder();
        volatile boolean handoffStop;
        boolean offerSent, romRequested, romReady;
        long handoffStarted, romBytesSent;
        UUID failedOffload;
        long lastClientVideo, lastClientAudio, lastCheckpointAt;
        long checkpointBytes;
        long demandEpoch = -1;
        boolean relayAudio, relayVideo;
        float ownerGain = -1;
        Thread thread;
        boolean forceKeyframe = true;
        long nextStream, nextKeyframe;
        long audioSequence;

        /** Fixed at power-on: components are locked while the console runs. */
        final HardwareProfile hardware;

        final int controllerPorts;
        volatile List<WorkerProcess.Rumble> rumble = List.of();

        /** Serial link: emulated on the server so both cores share a loopback socket. */
        final boolean linked;

        volatile int linkPort = -1;
        GameLinkBridge linkBridge;
        volatile Session linkPartner;
        volatile HardwareProfile linkOptions =
                HardwareProfile.options("gambatte_gb_link_mode", "Not Connected");
        volatile Boolean linkSupported;

        Session(InstanceStore.Metadata data, RomIndex.Game game, ConsoleHost host, String core) {
            this.data = data;
            this.game = game;
            this.host = host;
            var installed = host.installedComponents();
            hardware = host.definition().hardware(core, installed);
            controllerPorts = host.definition().controllerPorts(installed);
            linked = host.definition().links(installed);
        }
    }

    private record UploadChunk(UUID player, BlobTransfer.Chunk chunk) {}

    public InstanceManager(MinecraftServer server, RetrocraftConfig config) throws IOException {
        this(server, config, server.getWorldPath(LevelResource.ROOT));
    }

    public InstanceManager(MinecraftServer server, RetrocraftConfig config, Path worldRoot)
            throws IOException {
        this.server = server;
        this.config = config;
        store = new InstanceStore(worldRoot.resolve("retrocraft/instances"));
        saves = new GameSaves(RetrocraftConfig.savesRoot());
        romLibrary = new RomLibrary(RetrocraftConfig.root().resolve("roms"));
        biosLibrary = new RomLibrary(RetrocraftConfig.root().resolve("bios"));
        scanning = true;
        Thread.ofVirtual()
                .name("retrocraft-server-init")
                .start(
                        () -> {
                            try {
                                CacheMigration.prepare(
                                        RetrocraftConfig.root(),
                                        RetrocraftConfig.cacheRoot(),
                                        false);
                                Path jar = WorkerRuntime.extract(RetrocraftConfig.runtimeCache());
                                RomIndex catalog = loadLibrary(false, false);
                                callbacks.add(
                                        () -> {
                                            scanning = false;
                                            workerJar = jar;
                                            cores =
                                                    new CoreManager(
                                                            RetrocraftConfig.coreCache(), jar);
                                            roms = catalog;
                                            libraryUpdated();
                                            Retrocraft.LOG.info(
                                                    "Retrocraft ready: {} consoles, {} ROMs",
                                                    ConsoleRegistry.consoles().size(),
                                                    roms.catalog().size());
                                            if (pendingScan) {
                                                boolean onlyNew = pendingOnlyNew;
                                                pendingScan = false;
                                                rescan(null, onlyNew);
                                            }
                                        });
                            } catch (Exception ex) {
                                Retrocraft.LOG.error("Retrocraft server initialization failed", ex);
                                callbacks.add(() -> scanning = false);
                            }
                        });
    }

    private RomIndex loadLibrary(boolean force, boolean onlyNew) throws IOException {
        var previous =
                onlyNew && roms != null
                        ? roms.games().stream()
                                .map(
                                        g ->
                                                new RomScanner.Game(
                                                        g.cartridge().system().id().toString(),
                                                        g.id(),
                                                        g.name(),
                                                        g.filename(),
                                                        g.source()))
                                .toList()
                        : null;
        return RomIndex.load(
                RetrocraftConfig.root().resolve("roms"),
                RetrocraftConfig.serverCache(),
                config.maxRomSizeMiB * 1024L * 1024,
                force,
                onlyNew,
                previous,
                warning -> Retrocraft.LOG.warn("ROM scan: {}", warning));
    }

    public RetrocraftConfig config() {
        return config;
    }

    /** Applies a config object as-is (used by /retrocraft config set after a successful save). */
    public void applyConfig() {
        if (roms != null) libraryUpdated(); // core mappings may have changed
        biosManifestSent.clear();
        biosReadyClients.clear();
        readyClients.clear();
        for (var player : server.getPlayerList().getPlayers()) sendCoreSettings(player);
    }

    /** /retrocraft reload: re-reads server.json from disk and applies it. */
    public boolean reloadConfig(CommandSourceStack source) {
        try {
            config = RetrocraftConfig.load();
            applyConfig();
            return true;
        } catch (IOException ex) {
            Retrocraft.LOG.error("Cannot reload Retrocraft config", ex);
            return false;
        }
    }

    public boolean reloadLibrary(CommandSourceStack source) {
        return reloadLibrary(source, false);
    }

    public boolean reloadLibrary(CommandSourceStack source, boolean onlyNew) {
        if (scanning || stopping) return false;
        startScan(source, onlyNew);
        return true;
    }

    /**
     * A library mutation from the operator GUI: rescan now, coalescing bursts and retrying after a
     * busy scan.
     */
    private String scanActor;

    public void rescan() {
        rescan(null);
    }

    public void rescan(String actor) {
        rescan(actor, false);
    }

    public void rescan(String actor, boolean onlyNew) {
        if (stopping) return;
        if (actor != null) scanActor = actor;
        if (scanning) {
            pendingOnlyNew = pendingScan ? pendingOnlyNew && onlyNew : onlyNew;
            pendingScan = true;
            return;
        }
        startScan(null, onlyNew);
    }

    private void broadcastScan(boolean started) {
        var packet = new SettingsPackets.ScanEvent(scanActor == null ? "" : scanActor, started);
        for (var player : server.getPlayerList().getPlayers())
            if (Network.canSend(player, SettingsPackets.ScanEvent.ID)) send(player, packet);
    }

    private void startScan(CommandSourceStack source, boolean onlyNew) {
        scanning = true;
        broadcastScan(true);
        if (source != null)
            source.sendSuccess(
                    () -> Component.translatable("message.retrocraft.scan_started"), false);
        Thread.ofVirtual()
                .name("retrocraft-rom-scan")
                .start(
                        () -> {
                            try {
                                RomIndex result = loadLibrary(true, onlyNew);
                                callbacks.add(
                                        () -> {
                                            scanning = false;
                                            roms = result;
                                            libraryUpdated();
                                            broadcastScan(false);
                                            if (source != null)
                                                source.sendSuccess(
                                                        () ->
                                                                Component.translatable(
                                                                        "message.retrocraft.reloaded",
                                                                        roms.games().size()),
                                                        false);
                                            else
                                                Retrocraft.LOG.info(
                                                        "ROM catalog updated: {} ROMs",
                                                        roms.games().size());
                                            if (pendingScan) {
                                                pendingScan = false;
                                                rescan(null, pendingOnlyNew);
                                            }
                                        });
                            } catch (Exception ex) {
                                Retrocraft.LOG.error("Cannot scan ROM catalog", ex);
                                callbacks.add(
                                        () -> {
                                            scanning = false;
                                            broadcastScan(false);
                                            if (source != null)
                                                source.sendFailure(
                                                        Component.translatable(
                                                                "message.retrocraft.scan_failed"));
                                            if (pendingScan) {
                                                pendingScan = false;
                                                rescan(null, pendingOnlyNew);
                                            }
                                        });
                            }
                        });
    }

    private void libraryUpdated() {
        for (Session session : sessions.values()) {
            RomIndex.Game replacement = roms.find(session.game.cartridge(), session.data.gameId());
            if (replacement != null) {
                if (session.game != replacement) {
                    session.clientFit = null;
                    session.checkingFit = false;
                }
                session.game = replacement;
                if (session.thread == null) session.failure = null;
            }
        }
        Set<String> needed =
                roms.games().stream().map(this::coreId).collect(Collectors.toUnmodifiableSet());
        boolean coresChanged = coreGeneration == 0 || !requiredCores.equals(needed);
        boolean catalogChanged = !publishedCatalog.equals(roms.catalog());
        requiredCores = needed;
        publishedCatalog = roms.catalog();
        if (coresChanged) {
            coreGeneration++;
            readyClients.clear();
            verifiedClients.clear();
            biosReadyClients.clear();
            biosManifestSent.clear();
        }
        for (String id : requiredCores)
            if (!preparedCores.containsKey(id) && preparingCores.add(id)) {
                Thread.ofVirtual()
                        .name("retrocraft-core-" + id)
                        .start(
                                () -> {
                                    try {
                                        var prepared = cores.prepare(id, downloads);
                                        callbacks.add(
                                                () -> {
                                                    preparingCores.remove(id);
                                                    preparedCores.put(id, prepared);
                                                    Retrocraft.LOG.info(
                                                            "Core {} ready: {} {}",
                                                            id,
                                                            prepared.identity().name(),
                                                            prepared.identity().version());
                                                    if (requiredCores.contains(id))
                                                        for (var player :
                                                                server.getPlayerList().getPlayers())
                                                            sendCoreSettings(player);
                                                });
                                    } catch (Exception ex) {
                                        Retrocraft.LOG.error(
                                                "Cannot prepare known core {}", id, ex);
                                        callbacks.add(() -> preparingCores.remove(id));
                                    }
                                });
            }
        if (catalogChanged) catalogGeneration++;
        failedDiscovery.clear();
        if (catalogChanged || coresChanged)
            for (var player : server.getPlayerList().getPlayers())
                sendCatalog(player, coresChanged);
    }

    public void sendCatalog(ServerPlayer player) {
        sendCatalog(player, true);
    }

    private void sendCatalog(ServerPlayer player, boolean settings) {
        if (roms == null) return;
        if (settings) sendCoreSettings(player);
        if (!Network.canSend(player, Packets.Catalog.ID)) return;
        List<RomCatalog.Entry> catalog = roms.catalog();
        if (catalog.isEmpty())
            send(player, new Packets.Catalog(catalogGeneration, true, true, List.of()));
        for (int i = 0; i < catalog.size(); i += 128)
            send(
                    player,
                    new Packets.Catalog(
                            catalogGeneration,
                            i == 0,
                            i + 128 >= catalog.size(),
                            catalog.subList(i, Math.min(i + 128, catalog.size()))));
    }

    private static boolean operator(ServerPlayer player) {
        return com.sammwy.retrocraft.compat.Access.operator(player);
    }

    /**
     * /retrocraft gui or a client's RequestOpenSettingsGUI: operators get the schema, anyone else a
     * denial.
     */
    public void openSettings(ServerPlayer player) {
        send(
                player,
                operator(player)
                        ? SettingsPackets.GuiOpen.of(config)
                        : SettingsPackets.GuiOpen.denied("operator"));
    }

    /**
     * Applies a batch of key/value edits. Validated on a probe copy first so a bad value cannot
     * half-apply.
     */
    public void applySettings(ServerPlayer player, SettingsPackets.ConfigApply packet) {
        if (!operator(player)) {
            send(
                    player,
                    new SettingsPackets.ConfigSaved(
                            false, "Operator permission required", List.of()));
            return;
        }
        String error = null;
        var gson = new Gson();
        RetrocraftConfig probe = gson.fromJson(gson.toJson(config), RetrocraftConfig.class);
        for (var change : packet.changes()) {
            try {
                ConfigKey.set(probe, change.key(), change.value());
            } catch (IllegalArgumentException ex) {
                error = change.key() + ": " + ex.getMessage();
                break;
            }
        }
        if (error == null) {
            try {
                probe.save();
                config = probe;
                applyConfig();
            } catch (IOException ex) {
                error = "Could not save the config file";
                Retrocraft.LOG.error("Cannot save Retrocraft config", ex);
            }
        }
        send(
                player,
                new SettingsPackets.ConfigSaved(
                        error == null,
                        error == null ? "" : error,
                        SettingsPackets.GuiOpen.schema(config)));
        if (error == null)
            Retrocraft.LOG.info(
                    "{} updated Retrocraft settings via GUI",
                    com.sammwy.retrocraft.compat.Profiles.name(player.getGameProfile()));
    }

    /**
     * RomManagementPacket: directory listings and library mutations, one request id per response.
     */
    public void romAction(ServerPlayer player, SettingsPackets.RomAction packet) {
        if (!operator(player)) {
            send(
                    player,
                    SettingsPackets.RomEvent.fail(
                            packet.request(), "Operator permission required"));
            return;
        }
        if (packet.area() != SettingsPackets.AREA_ROMS
                && packet.area() != SettingsPackets.AREA_BIOS) {
            send(player, SettingsPackets.RomEvent.fail(packet.request(), "Unknown library area"));
            return;
        }
        if (packet.action() == SettingsPackets.RESCAN
                || packet.action() == SettingsPackets.RESCAN_NEW) {
            rescan(
                    com.sammwy.retrocraft.compat.Profiles.name(player.getGameProfile()),
                    packet.action() == SettingsPackets.RESCAN_NEW);
            send(
                    player,
                    new SettingsPackets.RomEvent(
                            packet.request(), SettingsPackets.ACTION, true, ""));
            return;
        }
        boolean bios = packet.area() == SettingsPackets.AREA_BIOS;
        try {
            libraryIo.execute(
                    () -> {
                        try {
                            RomLibrary target = library(bios);
                            String path = bios ? BiosFiles.canonical(packet.path()) : packet.path();
                            String destination =
                                    bios ? BiosFiles.canonical(packet.target()) : packet.target();
                            SettingsPackets.RomList listing = null;
                            switch (packet.action()) {
                                case SettingsPackets.LIST -> {
                                    var result = target.list(path);
                                    listing =
                                            new SettingsPackets.RomList(
                                                    packet.request(),
                                                    packet.path(),
                                                    result.truncated(),
                                                    result.entries().stream()
                                                            .map(
                                                                    e ->
                                                                            new SettingsPackets
                                                                                    .RomList.Entry(
                                                                                    e.name(),
                                                                                    e.directory(),
                                                                                    e.size()))
                                                            .toList());
                                }
                                case SettingsPackets.DELETE -> target.delete(path);
                                case SettingsPackets.RENAME -> target.rename(path, packet.target());
                                case SettingsPackets.MOVE -> target.move(path, destination);
                                case SettingsPackets.MKDIR -> target.mkdir(path);
                                default -> throw new IOException("Unknown action");
                            }
                            SettingsPackets.RomList response = listing;
                            callbacks.add(
                                    () -> {
                                        if (server.getPlayerList().getPlayer(player.getUUID())
                                                != player) return;
                                        if (response != null) send(player, response);
                                        else romChanged(player, packet.request(), !bios);
                                    });
                        } catch (IOException | RuntimeException ex) {
                            callbacks.add(
                                    () -> {
                                        if (server.getPlayerList().getPlayer(player.getUUID())
                                                == player)
                                            send(
                                                    player,
                                                    SettingsPackets.RomEvent.fail(
                                                            packet.request(),
                                                            ex.getMessage() == null
                                                                    ? "Operation failed"
                                                                    : ex.getMessage()));
                                    });
                        }
                    });
        } catch (RejectedExecutionException ex) {
            send(
                    player,
                    SettingsPackets.RomEvent.fail(packet.request(), "Library busy; try again"));
        }
    }

    private RomLibrary library(boolean bios) {
        return bios ? biosLibrary : romLibrary;
    }

    private void romChanged(ServerPlayer player, int request, boolean rescan) {
        send(player, new SettingsPackets.RomEvent(request, SettingsPackets.ACTION, true, ""));
        if (rescan) rescan(com.sammwy.retrocraft.compat.Profiles.name(player.getGameProfile()));
        else biosUpdated();
    }

    private void biosUpdated() {
        biosManifestSent.clear();
        biosReadyClients.clear();
        readyClients.clear();
        for (var player : server.getPlayerList().getPlayers()) sendCoreSettings(player);
    }

    private static final class Upload {
        final int id;
        final int area;
        Downloads.Transfer progress;
        final String dir, name, sha256;
        final long total;
        final ByteArrayOutputStream data;
        long lastActivity;

        Upload(int id, int area, String dir, String name, long total, String sha256) {
            this.id = id;
            this.area = area;
            this.dir = dir;
            this.name = name;
            this.sha256 = sha256;
            this.total = total;
            data = new ByteArrayOutputStream((int) Math.min(total, 1 << 20));
            lastActivity = System.nanoTime();
        }
    }

    /**
     * Chunked ROM upload; one active transfer per operator, staged in memory and verified before it
     * lands.
     */
    public void romUpload(ServerPlayer player, SettingsPackets.RomUpload packet) {
        if (!operator(player)) return;
        switch (packet.phase()) {
            case SettingsPackets.BEGIN -> beginUpload(player, packet);
            case SettingsPackets.CHUNK -> uploadChunk(player, packet);
            case SettingsPackets.ABORT -> {
                Upload cancelled = romUploads.remove(player.getUUID());
                if (cancelled != null && cancelled.progress != null) cancelled.progress.cancel();
                send(
                        player,
                        new SettingsPackets.RomEvent(
                                packet.id(), SettingsPackets.UPLOAD_FAIL, true, "cancelled"));
            }
            default -> {}
        }
    }

    private void beginUpload(ServerPlayer player, SettingsPackets.RomUpload packet) {
        try {
            if (romUploads.size() >= 8 && !romUploads.containsKey(player.getUUID()))
                throw new IOException("Too many concurrent uploads");
            if (packet.total() < 1
                    || packet.total()
                            > Math.min(
                                    (long) config.maxRomSizeMiB * 1024 * 1024,
                                    BlobTransfer.MAX_ROM))
                throw new IOException("File outside the ROM size limit");
            if (!packet.sha256().matches("[0-9a-f]{64}")) throw new IOException("Invalid checksum");
            library(packet.area() == SettingsPackets.AREA_BIOS)
                    .destination(
                            packet.area() == SettingsPackets.AREA_BIOS
                                    ? BiosFiles.canonical(packet.dir())
                                    : packet.dir(),
                            packet.name()); // names, folder and collisions checked up front
            Upload previous = romUploads.remove(player.getUUID());
            if (previous != null && previous.progress != null) previous.progress.cancel();
            romUploads.put(
                    player.getUUID(),
                    new Upload(
                            packet.id(),
                            packet.area(),
                            packet.area() == SettingsPackets.AREA_BIOS
                                    ? BiosFiles.canonical(packet.dir())
                                    : packet.dir(),
                            packet.name(),
                            packet.total(),
                            packet.sha256()));
            romUploads.get(player.getUUID()).progress =
                    downloads.begin(
                            packet.area() == SettingsPackets.AREA_BIOS ? "BIOS" : "ROM",
                            packet.name(),
                            packet.total());
            send(
                    player,
                    new SettingsPackets.RomEvent(
                            packet.id(), SettingsPackets.UPLOAD_ACCEPT, true, ""));
        } catch (IOException | RuntimeException ex) {
            send(
                    player,
                    new SettingsPackets.RomEvent(
                            packet.id(),
                            SettingsPackets.UPLOAD_FAIL,
                            true,
                            ex.getMessage() == null ? "Upload rejected" : ex.getMessage()));
        }
    }

    private void uploadChunk(ServerPlayer player, SettingsPackets.RomUpload packet) {
        Upload upload = romUploads.get(player.getUUID());
        if (upload == null || upload.id != packet.id())
            return; // stale chunk of an aborted transfer
        try {
            if (packet.offset() != upload.data.size()
                    || packet.data().length < 1
                    || upload.data.size() + packet.data().length > upload.total)
                throw new IOException("Chunk outside transfer bounds");
            upload.data.writeBytes(packet.data());
            upload.progress.update(upload.data.size(), upload.total);
            upload.lastActivity = System.nanoTime();
            if (upload.data.size() < upload.total) return;
            romUploads.remove(player.getUUID());
            UUID playerId = player.getUUID();
            String actor = com.sammwy.retrocraft.compat.Profiles.name(player.getGameProfile());
            libraryIo.execute(
                    () -> {
                        try {
                            byte[] bytes = upload.data.toByteArray();
                            if (!BlobTransfer.hash(bytes).equals(upload.sha256))
                                throw new IOException("Upload checksum mismatch");
                            boolean bios = upload.area == SettingsPackets.AREA_BIOS;
                            RomLibrary target = library(bios);
                            int landed;
                            landed = target.install(upload.dir, upload.name, bytes, bios);
                            upload.progress.complete();
                            Retrocraft.LOG.info(
                                    "{} uploaded {} {}", actor, bios ? "BIOS" : "ROM", upload.name);
                            callbacks.add(
                                    () -> {
                                        var current = server.getPlayerList().getPlayer(playerId);
                                        if (current != null)
                                            send(
                                                    current,
                                                    new SettingsPackets.RomEvent(
                                                            packet.id(),
                                                            SettingsPackets.UPLOAD_DONE,
                                                            true,
                                                            landed > 1
                                                                    ? landed + " files"
                                                                    : upload.name));
                                        if (bios) biosUpdated();
                                        else rescan(actor);
                                    });
                        } catch (IOException | RuntimeException ex) {
                            upload.progress.fail();
                            callbacks.add(
                                    () -> {
                                        var current = server.getPlayerList().getPlayer(playerId);
                                        if (current != null)
                                            send(
                                                    current,
                                                    new SettingsPackets.RomEvent(
                                                            packet.id(),
                                                            SettingsPackets.UPLOAD_FAIL,
                                                            true,
                                                            ex.getMessage() == null
                                                                    ? "Upload failed"
                                                                    : ex.getMessage()));
                                    });
                        }
                    });
        } catch (IOException | RuntimeException ex) {
            upload.progress.fail();
            romUploads.remove(player.getUUID());
            send(
                    player,
                    new SettingsPackets.RomEvent(
                            packet.id(),
                            SettingsPackets.UPLOAD_FAIL,
                            true,
                            ex.getMessage() == null ? "Upload failed" : ex.getMessage()));
        }
    }

    public void powerOn(ConsoleHost host, ServerPlayer player) {
        if (stopping || !host.canAccess(player) || host.instance() != null) return;
        if (roms == null) {
            error(player, "core_preparing");
            return;
        }
        if (activeCount() + pendingStarts.size() >= config.maxActiveInstances) {
            error(player, "limit");
            return;
        }
        ItemStack cartridge = host.cartridge();
        if (!cartridge.is(host.definition().cartridge())) {
            error(player, "insert_cartridge");
            return;
        }
        RomIndex.Game game =
                roms.find(host.definition().cartridge(), CartridgeItem.gameId(cartridge));
        if (game == null) {
            error(player, "invalid_rom");
            return;
        }
        if (host.definition().usesTelevision()) {
            if (!(host instanceof DockedConsoleBlockEntity console)
                    || Televisions.nearestFree(console) == null) {
                error(player, "no_tv");
                return;
            }
        }
        String coreId = coreId(game);
        CoreManager.Prepared prepared = preparedCores.get(coreId);
        if (prepared == null) {
            error(player, "core_preparing");
            return;
        }
        Object key = host instanceof HandheldHost handheld ? handheld.stack() : host;
        if (pendingStarts.containsKey(key)) return;
        PendingStart pending = new PendingStart();
        pendingStarts.put(key, pending);
        UUID playerId = player.getUUID();
        String consoleId = host.definition().id().toString();
        Thread.ofVirtual()
                .name("retrocraft-power-on")
                .start(
                        () -> {
                            try {
                                requireBios(coreId, game);
                                var data =
                                        store.create(
                                                playerId,
                                                consoleId,
                                                game.id(),
                                                game.name(),
                                                prepared.identity().sha256());
                                pending.created = data.id();
                                restoreSave(data);
                                if (stopping) {
                                    destroyFiles(data.id());
                                    return;
                                }
                                callbacks.add(
                                        () -> {
                                            pendingStarts.remove(key);
                                            ServerPlayer current =
                                                    server.getPlayerList().getPlayer(playerId);
                                            if (stopping
                                                    || current == null
                                                    || !host.valid()
                                                    || host.instance() != null
                                                    || !game.id()
                                                            .equals(
                                                                    CartridgeItem.gameId(
                                                                            host.cartridge()))
                                                    || activeCount() >= config.maxActiveInstances) {
                                                Thread.ofVirtual()
                                                        .name("retrocraft-cancel-power-on")
                                                        .start(() -> destroyFiles(data.id()));
                                                return;
                                            }
                                            TvBlockEntity tv =
                                                    host instanceof DockedConsoleBlockEntity console
                                                                    && host.definition()
                                                                            .usesTelevision()
                                                            ? Televisions.nearestFree(console)
                                                            : null;
                                            if (host.definition().usesTelevision() && tv == null) {
                                                Thread.ofVirtual()
                                                        .name("retrocraft-cancel-power-on")
                                                        .start(() -> destroyFiles(data.id()));
                                                error(current, "no_tv");
                                                return;
                                            }
                                            host.instance(data.id());
                                            if (tv != null)
                                                Televisions.bind(
                                                        (DockedConsoleBlockEntity) host,
                                                        tv,
                                                        data.id());
                                            sessions.put(
                                                    data.id(),
                                                    new Session(data, game, host, coreId(game)));
                                            if (host.canAccess(current)
                                                    && current.containerMenu
                                                            instanceof ConsoleScreenHandler screen
                                                    && screen.host() == host)
                                                ConsoleMenus.open(current, host);
                                        });
                            } catch (IOException | IllegalArgumentException ex) {
                                if (pending.created != null) destroyFiles(pending.created);
                                Retrocraft.LOG.error("Cannot power on console", ex);
                                callbacks.add(
                                        () -> {
                                            pendingStarts.remove(key);
                                            if (host.canAccess(player)) {
                                                if (ex instanceof BiosFiles.MissingException
                                                        || ex.getMessage() != null
                                                                && ex.getMessage()
                                                                        .startsWith(
                                                                                "PS2 BIOS missing"))
                                                    error(player, ex, null);
                                                else error(player, "storage_failed");
                                            }
                                        });
                            }
                        });
    }

    /** A bound television was broken: its console goes dark too. */
    public void televisionRemoved(TvBlockEntity tv) {
        if (tv.console() == null || tv.getLevel() == null) return;
        if (tv.getLevel().getBlockEntity(tv.console()) instanceof DockedConsoleBlockEntity console
                && tv.getBlockPos().equals(console.tv())) powerOff(console);
    }

    /**
     * Restore a powered item/block after joining, chunk load, inventory transfer or server restart.
     */
    public void discover(ConsoleHost host) {
        UUID id = host.instance();
        if (stopping || roms == null || id == null || !host.valid() || failedDiscovery.contains(id))
            return;
        Session existing = sessions.get(id);
        if (existing != null) {
            if (!existing.host.valid()) existing.host = host;
            return;
        }
        try {
            var data = store.load(id);
            RomIndex.Game game =
                    roms.find(
                            host.definition().cartridge(), CartridgeItem.gameId(host.cartridge()));
            if (game == null
                    || !host.cartridge().is(host.definition().cartridge())
                    || !data.gameId().equals(game.id())
                    || !data.console().equals(host.definition().id().toString()))
                throw new IOException("Console/cartridge identity mismatch");
            sessions.put(id, new Session(data, game, host, coreId(game)));
        } catch (IOException ex) {
            failedDiscovery.add(id);
            Retrocraft.LOG.error("Cannot discover powered console {}", id, ex);
        }
    }

    public void powerOff(ConsoleHost host) {
        UUID id = host.instance();
        if (id == null) return;
        host.instance(null);
        if (host instanceof DockedConsoleBlockEntity console) Televisions.unbind(console);
        failedDiscovery.remove(id);
        Session session = sessions.get(id);
        if (session != null) {
            if (session.linkPartner != null) disconnectLink(session);
            if (session.ownership.owner() != null) revoke(session, false);
            session.poweredOff = true;
            session.destroy = true;
            session.observed = false;
            stopSubscribers(session);
            if (session.thread != null && session.thread.isAlive()) return;
            sessions.remove(id);
            Thread.ofVirtual()
                    .name("retrocraft-console-destroy")
                    .start(() -> destroySession(session));
            return;
        }
        Thread.ofVirtual().name("retrocraft-console-destroy").start(() -> destroyFiles(id));
    }

    public void watchVideo(ServerPlayer player, ConsoleHost host) {
        if (!host.canAccess(player) || host.instance() == null) return;
        discover(host);
        Session session = sessions.get(host.instance());
        if (session == null || session.destroy) {
            error(player, "invalid_instance");
            return;
        }
        if (session.failure != null) {
            error(player, session.failure, session);
            return;
        }
        detachVideo(player.getUUID());
        VideoWatcher existing = session.videoWatchers.get(player.getUUID());
        if (existing != null) {
            existing.ambient = false;
            existing.lastSeen = System.nanoTime();
            existing.lastAcknowledged = existing.lastSent;
            existing.needsKeyframe = true;
        } else session.videoWatchers.put(player.getUUID(), new VideoWatcher());
        videoWatching.put(player.getUUID(), session.data.id());
        session.forceKeyframe = true;
        joinInput(player, session.data.id());
        send(player, status(session, player, Packets.PLAY));
        notifyPlayers(session);
    }

    public boolean needsReset(ConsoleHost host) {
        if (host.instance() == null) return false;
        discover(host);
        Session session = sessions.get(host.instance());
        return session == null || session.failure != null || session.destroy;
    }

    private static int maxSlots(Session session) {
        return Math.min(WorkerProtocol.PORTS, session.controllerPorts);
    }

    private static int slotOf(Session session, UUID player) {
        for (int slot = 0; slot < session.pads.length; slot++)
            if (session.pads[slot] != null && session.pads[slot].player.equals(player)) return slot;
        return -1;
    }

    /**
     * Right-clicking a powered docked console grabs the next free controller slot; handhelds stay
     * single-player.
     */
    public boolean joinInput(ServerPlayer player, UUID id) {
        Session session = sessions.get(id);
        if (session == null || session.destroy || !session.host.canAccess(player)) return false;
        if (slotOf(session, player.getUUID()) >= 0) {
            inputSessions.put(player.getUUID(), id);
            return true;
        }
        int slot = -1;
        for (int i = 0; i < maxSlots(session); i++)
            if (session.pads[i] == null) {
                slot = i;
                break;
            }
        if (slot < 0) {
            error(player, "input_busy");
            return false;
        }
        releaseInput(player.getUUID());
        session.pads[slot] = new InputSource(player.getUUID());
        inputSessions.put(player.getUUID(), id);
        session.failedOffload = null;
        notifyRoles(session);
        notifyPlayers(session);
        return true;
    }

    public boolean isInputSource(UUID player, UUID instance) {
        if (instance == null || !instance.equals(inputSessions.get(player))) return false;
        Session session = sessions.get(instance);
        return session != null && slotOf(session, player) >= 0;
    }

    public void releaseInput(UUID player) {
        Session session = sessions.get(inputSessions.remove(player));
        if (session == null) return;
        int slot = slotOf(session, player);
        if (slot < 0) return;
        session.pads[slot] = null;
        publishInputs(session, System.nanoTime());
        if (slot == 0) returnOwnership(session);
        broadcastPad(session, slot, PadInput.IDLE);
        notifyRoles(session);
        notifyPlayers(session);
    }

    /**
     * The picture is drawn on a block: the arcade cabinet's own CRT or the television bound to a
     * docked console.
     */
    private static boolean inWorld(Session session) {
        var definition = session.host.definition();
        return definition.type().isPlaced()
                && (!definition.usesTelevision()
                        || session.host instanceof DockedConsoleBlockEntity console
                                && console.tv() != null);
    }

    private void detachVideo(UUID player) {
        Session session = sessions.get(videoWatching.remove(player));
        if (session != null) {
            session.videoWatchers.remove(player);
            if (session.videoWatchers.isEmpty()) resetVideo(session);
        }
        releaseInput(player);
    }

    public void preferences(ServerPlayer player, Packets.Preferences packet) {
        preferences.put(player.getUUID(), packet);
    }

    private Packets.Preferences preferences(UUID player) {
        return preferences.getOrDefault(player, Packets.Preferences.DEFAULT);
    }

    public void disconnect(UUID player) {
        preferences.remove(player);
        for (Session session : sessions.values())
            if (player.equals(session.ownership.owner())) revoke(session, false);
        readyClients.remove(player);
        verifiedClients.remove(player);
        biosReadyClients.remove(player);
        clientGenerations.remove(player);
        outbound.remove(player);
        Upload cancelledUpload = romUploads.remove(player);
        if (cancelledUpload != null && cancelledUpload.progress != null)
            cancelledUpload.progress.cancel();
        downloadNotified.remove(player);
        biosManifestSent.remove(player);
        lastLinkRequest.remove(player);
        biosPending.remove(player);
        biosOutgoing.remove(player);
        detachVideo(player);
        for (Session session : sessions.values()) {
            session.audioWatchers.remove(player);
            session.videoWatchers.remove(player);
        }
    }

    public void request(ServerPlayer player, Packets.Request packet) {
        if (stopping) return;
        UUID id = packet.instance();
        Session session = sessions.get(id);
        if (session == null || session.destroy) return;
        UUID playerId = player.getUUID();
        VideoWatcher video = session.videoWatchers.get(playerId);
        long now = System.nanoTime();
        switch (packet.action()) {
            case Packets.AUDIO_ACK -> {
                AudioWatcher watcher = session.audioWatchers.get(playerId);
                if (watcher != null)
                    watcher.lastAcknowledged =
                            Math.max(
                                    watcher.lastAcknowledged,
                                    Math.min(watcher.lastSent, packet.acknowledgedSequence()));
            }
            case Packets.VIDEO_ACK -> {
                if (video != null) {
                    video.lastSeen = now;
                    video.lastAcknowledged =
                            Math.max(
                                    video.lastAcknowledged,
                                    Math.min(video.lastSent, packet.acknowledgedSequence()));
                }
            }
            case Packets.INPUT -> {
                if (video != null) {
                    video.lastSeen = now;
                    video.lastAcknowledged =
                            Math.max(
                                    video.lastAcknowledged,
                                    Math.min(video.lastSent, packet.acknowledgedSequence()));
                }
                int slot = slotOf(session, playerId);
                InputSource input = slot >= 0 ? session.pads[slot] : null;
                if (input != null && session.host.canAccess(player)) {
                    PadInput state = packet.input();
                    if (!input.state.equals(state)) {
                        input.state = state;
                        broadcastPad(session, slot, state);
                    }
                    input.lastSeen = now;
                    publishInputs(session, now);
                }
            }
            case Packets.CLOSE -> {
                if (video != null) detachVideo(playerId);
            }
            case Packets.RESYNC -> {
                if (video == null || now - video.lastResync < TimeUnit.SECONDS.toNanos(1)) return;
                video.lastResync = now;
                video.lastAcknowledged = video.lastSent;
                video.needsKeyframe = true;
                session.forceKeyframe = true;
            }
            case Packets.POWER_OFF -> {
                if (!session.host.canAccess(player)) return;
                ConsoleHost host = session.host;
                powerOff(host);
                ConsoleMenus.inventory(player, host);
            }
            case Packets.EJECT -> {
                if (session.host.hasInternalRom() || !session.host.canAccess(player)) return;
                ConsoleHost host = session.host;
                powerOff(host);
                ItemStack cartridge = host.cartridge();
                host.cartridge(ItemStack.EMPTY);
                if (!player.getInventory().add(cartridge)) player.drop(cartridge, false);
                ConsoleMenus.inventory(player, host);
            }
            case Packets.CLAIM_INPUT -> joinInput(player, id);
            default -> {}
        }
    }

    private void notifyDownloads() {
        long now = System.nanoTime();
        if (now < nextDownloadNotice) return;
        nextDownloadNotice = now + 250_000_000L;
        var entries = downloads.entries();
        for (var player : server.getPlayerList().getPlayers()) {
            if (!operator(player)) continue;
            if (entries.isEmpty()) {
                if (downloadNotified.remove(player.getUUID()))
                    player.displayClientMessage(Component.empty(), true);
                continue;
            }
            int index = (int) ((now / 2_000_000_000L) % entries.size());
            var entry = entries.get(index);
            int percent = entry.percent();
            int filled = percent < 0 ? (int) ((now / 250_000_000L) % 10) : percent / 10;
            String bar = "▰".repeat(filled) + "▱".repeat(10 - filled);
            String status =
                    switch (entry.state()) {
                        case DOWNLOADING -> "Downloading ";
                        case COMPLETE -> "Downloaded ";
                        case FAILED -> "Download failed: ";
                    };
            String name =
                    entry.name().length() > 36 ? entry.name().substring(0, 33) + "…" : entry.name();
            String text =
                    "Retrocraft · "
                            + status
                            + entry.kind()
                            + " · "
                            + name
                            + "  "
                            + bar
                            + (percent < 0 ? "" : " " + percent + "%")
                            + " · "
                            + entry.size()
                            + (entries.size() > 1
                                    ? " [" + (index + 1) + "/" + entries.size() + "]"
                                    : "");
            player.displayClientMessage(
                    Component.literal(text)
                            .withStyle(
                                    style ->
                                            style.withColor(
                                                    entry.state() == Downloads.State.FAILED
                                                            ? 0xE0655C
                                                            : 0xC45CF0)),
                    true);
            downloadNotified.add(player.getUUID());
        }
    }

    public void tick() {
        Runnable callback;
        for (int i = 0; i < 256 && (callback = callbacks.poll()) != null; i++)
            if (!stopping) callback.run();
        flushTransfers();
        flushBios();
        notifyDownloads();
        if (roms == null) return;
        ticks++;
        tickLinks();
        if (ticks % 20 == 1) discoverItems();
        if (ticks % (20 * config.thumbnailIntervalSeconds) == 1) markThumbnails();
        if (ticks % (20 * 3600) == 2)
            Thread.ofVirtual().name("retrocraft-saves-cleanup").start(this::cleanOrphanSaves);
        long now = System.nanoTime();
        for (var it = romUploads.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            if (now - entry.getValue().lastActivity <= TimeUnit.SECONDS.toNanos(60)) continue;
            it.remove();
            entry.getValue().progress.fail();
            var player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null)
                send(
                        player,
                        new SettingsPackets.RomEvent(
                                entry.getValue().id,
                                SettingsPackets.UPLOAD_FAIL,
                                true,
                                "Upload timed out"));
        }
        for (Session session : List.copyOf(sessions.values())) {
            finish(session);
            if (session.destroy) {
                if (session.thread == null) {
                    sessions.remove(session.data.id());
                    Thread.ofVirtual()
                            .name("retrocraft-console-destroy")
                            .start(() -> destroySession(session));
                }
                continue;
            }
            updateSubscriptions(session, now);
            sendDemand(session);
            session.wantsVideo = !session.videoWatchers.isEmpty();
            session.wantsAudio = !session.audioWatchers.isEmpty();
            session.observed = session.wantsVideo || session.wantsAudio;
            if (session.ownership.expired(now)) revoke(session, true);
            if (session.incoming.values().stream().anyMatch(receiver -> receiver.reserved() > 0)
                    && now - session.lastIncomingChunk > TimeUnit.SECONDS.toNanos(15))
                revoke(session, true);
            if (session.ownership.mode() == Ownership.Mode.CLIENT
                    && now - session.lastCheckpointAt
                            > TimeUnit.SECONDS.toNanos(
                                    BlobTransfer.timeoutSeconds(
                                            Math.max(
                                                    config.ownerTimeoutSeconds,
                                                    config.checkpointIntervalSeconds * 3L + 5),
                                            session.checkpointBytes,
                                            2))) revoke(session, true);
            if (session.thread == null
                    && session.observed
                    && session.failure == null
                    && session.ownership.serverMayRun()
                    && serverMayEmulate(session)
                    && preparedCores.containsKey(coreId(session.game))
                    && activeCount() < config.maxActiveInstances) start(session);
            coordinateOwnership(session, now);
            if (session.thread == null
                    && !session.host.valid()
                    && !session.observed
                    && session.ownership.serverMayRun()) {
                sessions.remove(session.data.id());
                Thread.ofVirtual()
                        .name("retrocraft-console-unload")
                        .start(() -> persistSave(session));
                continue;
            }
            if (ticks % 2 == 0 && session.process != null) {
                var strengths = session.rumble;
                for (int port = 0; port < Math.min(strengths.size(), session.pads.length); port++) {
                    var pad = session.pads[port];
                    if (pad == null) continue;
                    var player = server.getPlayerList().getPlayer(pad.player);
                    var strength = strengths.get(port);
                    if (player != null)
                        send(
                                player,
                                new Packets.Rumble(
                                        session.data.id(), strength.strong(), strength.weak()));
                }
            }
            stream(session, now);
        }
    }

    private void discoverItems() {
        for (var player : server.getPlayerList().getPlayers()) {
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (stack.getItem() instanceof HandheldConsoleItem
                        && HandheldConsoleItem.instance(stack) != null)
                    discover(new HandheldHost(player, stack));
            }
        }
        if (!itemsIndexed) {
            itemsIndexed = true;
            for (var world : server.getAllLevels())
                for (var entity : world.getAllEntities()) entityLoaded(entity);
        }
        droppedItems.removeIf(Entity::isRemoved);
        for (ItemEntity item : droppedItems)
            if (item.getItem().getItem() instanceof HandheldConsoleItem
                    && HandheldConsoleItem.instance(item.getItem()) != null)
                discover(new HandheldHost(item));
    }

    public void entityLoaded(Entity entity) {
        if (entity instanceof ItemEntity item) droppedItems.add(item);
    }

    public void entityUnloaded(Entity entity) {
        if (entity instanceof ItemEntity item) droppedItems.remove(item);
    }

    private static void publishInputs(Session session, long now) {
        PadInput[] masks = new PadInput[WorkerProtocol.PORTS];
        for (int port = 0; port < masks.length; port++) {
            InputSource source = session.pads[port];
            if (source != null && now - source.lastSeen < TimeUnit.SECONDS.toNanos(2))
                masks[port] = source.state;
        }
        if (!Arrays.equals(masks, session.inputMasks)) session.inputMasks = masks;
    }

    private void updateSubscriptions(Session session, long now) {
        for (int port = 0; port < session.pads.length; port++) {
            InputSource input = session.pads[port];
            if (input != null
                    && !input.state.equals(PadInput.IDLE)
                    && now - input.lastSeen >= TimeUnit.SECONDS.toNanos(2)) {
                input.state = PadInput.IDLE;
                broadcastPad(session, port, PadInput.IDLE);
            }
        }
        publishInputs(session, now);
        boolean valid = session.host.valid() && session.data.id().equals(session.host.instance());
        if (valid) session.invalidSince = 0;
        else if (session.invalidSince == 0) session.invalidSince = now;
        // A handheld being dropped or picked up keeps playing while discovery swaps the host.
        boolean grace = valid || now - session.invalidSince < TimeUnit.SECONDS.toNanos(3);
        for (UUID id : List.copyOf(session.videoWatchers.keySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            VideoWatcher watcher = session.videoWatchers.get(id);
            if (!valid
                    || player == null
                    || (!watcher.ambient && !session.host.canAccess(player))
                    || now - watcher.lastSeen
                            > TimeUnit.SECONDS.toNanos(config.watcherTimeoutSeconds)) {
                if (watcher.ambient) {
                    session.videoWatchers.remove(id);
                    continue;
                }
                if (player != null) send(player, stopped(session));
                detachVideo(id);
            }
        }
        Set<UUID> listeners = new HashSet<>(), viewers = new HashSet<>();
        boolean inWorld = inWorld(session);
        if (grace && session.failure == null)
            for (var player : server.getPlayerList().getPlayers()) {
                if (player.level() != session.host.world()) continue;
                double distanceSquared =
                        player.position().distanceToSqr(session.host.soundPosition());
                if (distanceSquared >= (double) config.audioRadius * config.audioRadius) continue;
                UUID id = player.getUUID();
                var personal = preferences(id);
                if (personal.audio() && Network.canSend(player, Packets.Audio.ID)) {
                    listeners.add(id);
                    session.audioWatchers.computeIfAbsent(id, ignored -> new AudioWatcher(1)).gain =
                            (float) (1 - Math.sqrt(distanceSquared) / config.audioRadius);
                }
                if (inWorld && personal.worldVideo() && Network.canSend(player, Packets.Video.ID)) {
                    viewers.add(id);
                    if (!session.videoWatchers.containsKey(id)) {
                        VideoWatcher watcher = new VideoWatcher();
                        watcher.ambient = true;
                        session.videoWatchers.put(id, watcher);
                        session.forceKeyframe = true;
                    }
                }
            }
        for (var entry : List.copyOf(session.videoWatchers.entrySet()))
            if (entry.getValue().ambient && !viewers.contains(entry.getKey())) {
                session.videoWatchers.remove(entry.getKey());
                if (session.videoWatchers.isEmpty()) resetVideo(session);
            }
        for (UUID id : List.copyOf(session.audioWatchers.keySet()))
            if (!listeners.contains(id)) {
                session.audioWatchers.remove(id);
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player != null) send(player, new Packets.AudioStop(session.data.id()));
            }
        for (int slot = 0; slot < session.pads.length; slot++) {
            InputSource input = session.pads[slot];
            if (input == null) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(input.player);
            if (!valid
                    || player == null
                    || !session.host.canAccess(player)
                    || now - input.lastSeen
                            > TimeUnit.SECONDS.toNanos(config.watcherTimeoutSeconds))
                releaseInput(input.player);
        }
    }

    private void stream(Session session, long now) {
        WorkerProcess.Output output = session.media.drain();
        if (output == null) return;
        if (output.frame() != null) {
            session.lastFrame = output.frame();
            if (session.thumbDue) {
                session.thumbDue = false;
                broadcastThumbnail(session);
            }
        }
        // Audio fan-out never depends on video acknowledgements or on an open HUD.
        if (output.audio().length > 0) session.audioSequence++;
        if (output.audio().length > 0)
            for (var listener : session.audioWatchers.entrySet()) {
                AudioWatcher watcher = listener.getValue();
                if (watcher.lastSent - watcher.lastAcknowledged >= 6) continue;
                var player = server.getPlayerList().getPlayer(listener.getKey());
                if (player != null
                        && !session.ownership.clientMayRun(
                                player.getUUID(), session.ownership.epoch())) {
                    send(
                            player,
                            new Packets.Audio(
                                    session.data.id(),
                                    session.audioSequence,
                                    session.sampleRate,
                                    watcher.gain,
                                    output.audio()));
                    watcher.lastSent = session.audioSequence;
                }
            }
        if (now + 1_000_000L < session.nextStream
                || session.lastFrame == null
                || session.videoWatchers.isEmpty()
                || session.encodingVideo) return;
        long period = 1_000_000_000L / Math.min(20, config.streamFps);
        session.nextStream = Math.max(session.nextStream, now - period) + period;
        List<UUID> targets = new ArrayList<>();
        for (var entry : session.videoWatchers.entrySet()) {
            if (session.ownership.clientMayRun(entry.getKey(), session.ownership.epoch())) continue;
            VideoWatcher watcher = entry.getValue();
            if (watcher.lastSent - watcher.lastAcknowledged >= 4) {
                watcher.needsKeyframe = true;
                if (now - watcher.lastResync < TimeUnit.SECONDS.toNanos(1)) continue;
                watcher.lastResync = now;
                watcher.lastAcknowledged = watcher.lastSent;
            }
            if (server.getPlayerList().getPlayer(entry.getKey()) != null)
                targets.add(entry.getKey());
            if (watcher.needsKeyframe) session.forceKeyframe = true;
        }
        if (targets.isEmpty()) return;
        boolean key = session.forceKeyframe || now >= session.nextKeyframe;
        if (key) {
            session.forceKeyframe = false;
            session.nextKeyframe = now + TimeUnit.SECONDS.toNanos(config.keyframeIntervalSeconds);
        }
        session.encodingVideo = true;
        int generation = session.videoGeneration;
        FrameCodec.Encoder encoder = session.encoder;
        FrameCodec.Frame picture = session.lastFrame;
        Thread.ofVirtual()
                .name("retrocraft-video-encode")
                .start(
                        () -> {
                            // Delivered as soon as the server thread is free, not on the next tick.
                            try {
                                var frame = encoder.encode(picture, key);
                                var packet = new Packets.Video(session.data.id(), frame);
                                server.execute(
                                        () -> {
                                            if (stopping || generation != session.videoGeneration)
                                                return;
                                            session.encodingVideo = false;
                                            for (UUID id : targets) {
                                                VideoWatcher watcher =
                                                        session.videoWatchers.get(id);
                                                ServerPlayer player =
                                                        server.getPlayerList().getPlayer(id);
                                                if (watcher == null
                                                        || player == null
                                                        || watcher.needsKeyframe && !key) continue;
                                                send(player, packet);
                                                watcher.lastSent = frame.sequence();
                                                watcher.needsKeyframe = false;
                                            }
                                        });
                            } catch (RuntimeException ex) {
                                server.execute(
                                        () -> {
                                            if (!stopping
                                                    && generation == session.videoGeneration) {
                                                session.encodingVideo = false;
                                                session.forceKeyframe = true;
                                                Retrocraft.LOG.error(
                                                        "Cannot encode console video", ex);
                                            }
                                        });
                            }
                        });
    }

    private static void resetVideo(Session session) {
        session.encoder = session.encoder.restart();
        session.videoGeneration++;
        session.encodingVideo = false;
        session.forceKeyframe = true;
        for (VideoWatcher watcher : session.videoWatchers.values()) {
            watcher.lastAcknowledged = watcher.lastSent;
            watcher.needsKeyframe = true;
        }
    }

    /** Native save files, linked serial ports and remote rumble must stay on the server. */
    private boolean serverOnly(Session session) {
        return "rumble".equals(session.hardware.options().get("parallel-n64-pak1"))
                || nativeFileSaves(session)
                || session.linked
                || session.linkPartner != null
                || KnownCores.get(coreId(session.game)).serverOnly();
    }

    private boolean nativeFileSaves(Session session) {
        String core = coreId(session.game);
        return core.equals("ppsspp")
                || core.equals(Components.BEETLE_PSX)
                        && "enabled"
                                .equals(
                                        session.hardware
                                                .options()
                                                .get("beetle_psx_enable_memcard1"))
                || core.equals(Components.FLYCAST);
    }

    private String coreId(RomIndex.Game game) {
        return config.coreId(game.cartridge().system().id().toString());
    }

    private boolean serverMayEmulate(Session session) {
        return config.emulationMode != RetrocraftConfig.EmulationMode.CLIENT || serverOnly(session);
    }

    private boolean clientMayEmulate() {
        return config.emulationMode != RetrocraftConfig.EmulationMode.SERVER;
    }

    /**
     * ROMs over the client pool bound never leave the server — hybrid degrades to server-side,
     * client mode just refuses.
     */
    private boolean clientFits(Session session) {
        if (serverOnly(session)) return false;
        if (session.clientFit != null) return session.clientFit;
        if (!session.checkingFit) {
            session.checkingFit = true;
            RomIndex.Game game = session.game;
            long limit = Math.min((long) config.maxRomSizeMiB * 1024 * 1024, BlobTransfer.MAX_ROM);
            Thread.ofVirtual()
                    .name("retrocraft-client-fit")
                    .start(
                            () -> {
                                boolean fits;
                                try {
                                    fits = Files.size(game.materialize()) <= limit;
                                } catch (IOException ex) {
                                    fits = false;
                                }
                                boolean result = fits;
                                callbacks.add(
                                        () -> {
                                            if (session.game == game) {
                                                session.clientFit = result;
                                                session.checkingFit = false;
                                            }
                                        });
                            });
        }
        return false;
    }

    /** Everyone who could glance at the console: trackers of its carrier plus the holder itself. */
    private Set<ServerPlayer> audience(HandheldHost host) {
        Entity carrier = host.carrier();
        if (carrier == null) return null;
        Set<ServerPlayer> audience = new HashSet<>(Network.tracking(carrier));
        if (carrier instanceof ServerPlayer player) audience.add(player);
        audience.removeIf(
                player ->
                        !preferences(player.getUUID()).thumbnails()
                                || !Network.canSend(player, Packets.Thumbnail.ID));
        return audience.isEmpty() ? null : audience;
    }

    /**
     * Every cycle: running handhelds capture a fresh frame on their next advance; paused ones
     * resend the cached preview.
     */
    private void markThumbnails() {
        for (Session session : sessions.values()) {
            if (session.destroy
                    || !(session.host instanceof HandheldHost host)
                    || audience(host) == null) continue;
            if (session.lastThumb != null)
                resendThumbnail(session, host); // cached preview always refreshes the watchers' TTL
            if (session.thread != null) session.thumbDue = true;
            else if (session.lastThumb == null && session.lastFrame != null)
                broadcastThumbnail(session);
        }
    }

    private void resendThumbnail(Session session, HandheldHost host) {
        Set<ServerPlayer> audience = audience(host);
        if (audience != null)
            for (var player : audience)
                send(player, new Packets.Thumbnail(session.data.id(), session.lastThumb));
    }

    private void broadcastThumbnail(Session session) {
        if (session.destroy
                || !(session.host instanceof HandheldHost host)
                || session.lastFrame == null) return;
        if (audience(host) == null) return;
        FrameCodec.Frame frame = session.lastFrame;
        if (session.lastThumbSource == frame && session.lastThumb != null) {
            resendThumbnail(session, host);
            return;
        }
        Thread.ofVirtual()
                .name("retrocraft-thumbnail")
                .start(
                        () -> {
                            try {
                                byte[] png = ThumbnailCodec.encode(frame);
                                callbacks.add(
                                        () -> {
                                            session.lastThumbSource = frame;
                                            session.lastThumb = png;
                                            if (!session.destroy
                                                    && session.host instanceof HandheldHost current)
                                                resendThumbnail(session, current);
                                        });
                            } catch (IOException ignored) {
                            }
                        });
    }

    /**
     * Hybrid owners push their own in-hand preview; the server adopts it as the session thumbnail
     * and fans it out.
     */
    public void thumbnailUpload(ServerPlayer player, Packets.ThumbnailUpload packet) {
        if (!config.thumbnailUpload || stopping) return;
        Session session = sessions.get(packet.instance());
        if (session == null
                || session.destroy
                || !(session.host instanceof HandheldHost host)
                || !session.ownership.clientMayRun(player.getUUID(), session.ownership.epoch()))
            return;
        byte[] png = packet.png();
        if (!ThumbnailCodec.valid(png)) return;
        long now = System.nanoTime();
        if (now - session.lastUploadAt < 1_000_000_000L) return;
        session.lastUploadAt = now;
        session.lastThumb = png;
        session.lastThumbSource = null;
        Set<ServerPlayer> audience = audience(host);
        if (audience != null)
            for (var watcher : audience)
                if (!watcher.getUUID().equals(player.getUUID()))
                    send(watcher, new Packets.Thumbnail(session.data.id(), png));
    }

    private long activeCount() {
        return sessions.values().stream().filter(s -> s.thread != null && !s.finished).count();
    }

    private void start(Session session) {
        session.handoffStop = false;
        RomIndex.Game game = session.game;
        session.thread =
                Thread.ofVirtual()
                        .name("retrocraft-" + session.data.id())
                        .unstarted(
                                () -> {
                                    Path state = store.snapshot(session.data.id());
                                    try {
                                        String coreId = coreId(game);
                                        CoreManager.Prepared prepared = preparedCores.get(coreId);
                                        if (prepared == null)
                                            throw new IOException("Core not prepared");
                                        requireBios(coreId, game);
                                        Path core = prepared.path();
                                        // The core hash is pinned at preparation and
                                        // materialize() re-verifies the ROM against its GameID.
                                        if (!game.id().equals(session.data.gameId())
                                                || !session.data
                                                        .coreSha256()
                                                        .equals(prepared.identity().sha256()))
                                            throw new IOException(
                                                    "ROM/core changed since snapshot");
                                        Path rom = game.materialize();
                                        try (var saveIo =
                                                        Executors.newSingleThreadExecutor(
                                                                Thread.ofVirtual()
                                                                        .name(
                                                                                "retrocraft-server-autosave")
                                                                        .factory());
                                                WorkerProcess process =
                                                        openServerProcess(
                                                                session, core, rom, state)) {
                                            Future<?> pendingSave = null;
                                            HardwareProfile appliedLink = null;
                                            session.process = process;
                                            session.sampleRate = process.sampleRate();
                                            long period =
                                                    Math.round(1_000_000_000d / process.fps());
                                            long next = System.nanoTime(), nextVideo = next;
                                            long nextSave =
                                                    next
                                                            + TimeUnit.SECONDS.toNanos(
                                                                    config.autosaveIntervalSeconds);
                                            while (!stopping
                                                    && !session.destroy
                                                    && !session.handoffStop
                                                    && session.ownership.serverMayRun()
                                                    && serverMayEmulate(session)
                                                    && (session.observed
                                                            || !process.supportsSnapshots())) {
                                                if (!session.observed) {
                                                    if (session.invalidSince > 0
                                                            && System.nanoTime()
                                                                            - session.invalidSince
                                                                    > TimeUnit.SECONDS.toNanos(3))
                                                        break;
                                                    LockSupport.parkNanos(
                                                            TimeUnit.MILLISECONDS.toNanos(50));
                                                    next = System.nanoTime();
                                                    continue;
                                                }
                                                if (coreId.equals(Components.GAMBATTE)
                                                        && appliedLink != session.linkOptions) {
                                                    HardwareProfile desired = session.linkOptions;
                                                    session.linkSupported =
                                                            process.options(desired);
                                                    appliedLink = desired;
                                                }
                                                long before = System.nanoTime();
                                                boolean video =
                                                        session.thumbDue
                                                                || session.wantsVideo
                                                                        && before >= nextVideo;
                                                if (video)
                                                    nextVideo =
                                                            before
                                                                    + 1_000_000_000L
                                                                            / Math.min(
                                                                                    20,
                                                                                    config.streamFps);
                                                var output =
                                                        process.advance(
                                                                session.inputMasks,
                                                                video,
                                                                session.wantsAudio);
                                                session.rumble = output.rumble();
                                                session.media.offer(output, process.sampleRate());
                                                if (before >= nextSave
                                                        && (pendingSave == null
                                                                || pendingSave.isDone())) {
                                                    if (pendingSave != null) pendingSave.get();
                                                    if (process.supportsSnapshots()) {
                                                        byte[] snapshot = process.capture();
                                                        pendingSave =
                                                                saveIo.submit(
                                                                        () -> {
                                                                            SaveStates.validate(
                                                                                    snapshot);
                                                                            SaveStates.writeAtomic(
                                                                                    state,
                                                                                    snapshot);
                                                                            byte[] battery =
                                                                                    GameSaves.sram(
                                                                                            snapshot);
                                                                            if (battery != null)
                                                                                SaveStates
                                                                                        .writeAtomic(
                                                                                                state
                                                                                                        .resolveSibling(
                                                                                                                "battery.sav"),
                                                                                                battery);
                                                                            persistSave(session);
                                                                            return null;
                                                                        });
                                                    } else {
                                                        process.saveBattery();
                                                        persistSave(session);
                                                    }
                                                    nextSave =
                                                            System.nanoTime()
                                                                    + TimeUnit.SECONDS.toNanos(
                                                                            config.autosaveIntervalSeconds);
                                                }
                                                long now = System.nanoTime();
                                                next += period;
                                                if (next < now - period * 4) next = now;
                                                if (next > now) LockSupport.parkNanos(next - now);
                                            }
                                            if (pendingSave != null) pendingSave.get();
                                            if (!session.destroy && process.supportsSnapshots())
                                                process.save();
                                        }
                                    } catch (Throwable ex) {
                                        session.failure = ex;
                                        Retrocraft.LOG.error(
                                                "Console instance {} failed",
                                                session.data.id(),
                                                ex);
                                    } finally {
                                        if (session.destroy) destroySession(session);
                                        session.process = null;
                                        session.media.clear();
                                        session.finished = true;
                                    }
                                });
        session.thread.start();
    }

    private WorkerProcess openServerProcess(Session session, Path core, Path rom, Path state)
            throws IOException {
        store.recover(session.data.id());
        Path log = store.directory(session.data.id()).resolve("worker.log");
        // The pool layout is POOL/platform/<coreId>/<binary> — the parent dir name is the trusted
        // id.
        Path bios = RetrocraftConfig.systemDir(core.getParent().getFileName().toString());
        Path nativeSaves =
                nativeFileSaves(session) && config.saveMode != RetrocraftConfig.SaveMode.NONE
                        ? saves.nativeDirectory(
                                session.data.owner(),
                                config.saveMode == RetrocraftConfig.SaveMode.GLOBAL,
                                session.data.gameId())
                        : null;
        HardwareProfile hardware = session.hardware;
        if (coreId(session.game).equals(Components.GAMBATTE))
            hardware = hardware.merge(session.linkOptions);
        try {
            return new WorkerProcess(workerJar, core, rom, state, bios, log, nativeSaves, hardware);
        } catch (IOException ex) {
            if (ex instanceof EOFException && workerLogSays(log, "Core rejected ROM"))
                throw new RomRejectedException(ex, rom);
            if (!Files.exists(store.backup(session.data.id()))) throw ex;
            Retrocraft.LOG.warn(
                    "Latest checkpoint rejected for {}; restoring previous complete state",
                    session.data.id());
            byte[] previous = Files.readAllBytes(store.backup(session.data.id()));
            SaveStates.validate(previous);
            SaveStates.writeAtomic(state, previous);
            return new WorkerProcess(workerJar, core, rom, state, bios, log, nativeSaves, hardware);
        }
    }

    /**
     * The worker died before the handshake and its log names the cause (e.g. MAME 2003-Plus
     * rejecting a mismatched romset).
     */
    static final class RomRejectedException extends IOException {
        final Path rom;

        RomRejectedException(Throwable cause, Path rom) {
            super("Core rejected ROM", cause);
            this.rom = rom;
        }
    }

    private static boolean workerLogSays(Path log, String needle) {
        try {
            return Files.exists(log) && Files.readString(log).contains(needle);
        } catch (IOException ex) {
            return false;
        }
    }

    private boolean linkConsole(Session session) {
        return session != null
                && !session.destroy
                && !session.poweredOff
                && session.failure == null
                && session.host instanceof HandheldHost
                && session.host.valid()
                && coreId(session.game).equals(Components.GAMBATTE);
    }

    private ServerPlayer linkPlayer(Session session) {
        if (!linkConsole(session) || session.pads[0] == null) return null;
        var player = server.getPlayerList().getPlayer(session.pads[0].player);
        return controlsHere(session, player) ? player : null;
    }

    private boolean linkNearby(Session a, Session b) {
        return linkConsole(a)
                && linkConsole(b)
                && a != b
                && a.host.world() == b.host.world()
                && a.host.position().distanceToSqr(b.host.position()) <= LINK_RANGE * LINK_RANGE;
    }

    private void linkNotice(ServerPlayer player, String key) {
        if (player != null)
            player.displayClientMessage(
                    Component.translatable("message.retrocraft.link." + key), true);
    }

    public void linkRequest(ServerPlayer player, LinkPackets.Request packet) {
        if (stopping) return;
        Session source = sessions.get(packet.instance());
        if (linkPlayer(source) != player) return;
        long now = System.nanoTime();
        if (packet.action() == LinkPackets.CANCEL) {
            var offer =
                    linkInvitations.cancel(packet.target(), packet.instance(), player.getUUID());
            if (offer != null) closeOffer(offer);
            return;
        }
        if (packet.action() == LinkPackets.DISCONNECT) {
            disconnectLink(source);
            return;
        }
        if (packet.action() == LinkPackets.ACCEPT || packet.action() == LinkPackets.DECLINE) {
            var pending =
                    linkInvitations.offers().stream()
                            .filter(o -> o.token().equals(packet.target()))
                            .findFirst()
                            .orElse(null);
            var offer =
                    linkInvitations.reply(
                            packet.target(), packet.instance(), player.getUUID(), now);
            if (offer == null) {
                if (pending != null
                        && pending.target().equals(packet.instance())
                        && pending.recipient().equals(player.getUUID())
                        && now >= pending.expires()) {
                    closeOffer(pending);
                    linkResult(pending, LinkPackets.EXPIRED);
                }
                return;
            }
            Session sender = sessions.get(offer.source());
            var senderPlayer = linkPlayer(sender);
            closeOffer(offer);
            if (packet.action() == LinkPackets.DECLINE) {
                linkResult(offer, LinkPackets.DECLINED);
                linkNotice(senderPlayer, "declined");
                return;
            }
            if (senderPlayer == null
                    || !senderPlayer.getUUID().equals(offer.sender())
                    || !linkNearby(sender, source)
                    || !sender.linked
                    || sender.linkPartner != null
                    || source.linkPartner != null
                    || Boolean.FALSE.equals(sender.linkSupported)
                    || Boolean.FALSE.equals(source.linkSupported)) {
                linkResult(offer, LinkPackets.UNAVAILABLE);
                linkNotice(player, "unavailable");
                return;
            }
            long required = activeCount();
            if (sender.thread == null || sender.finished) required++;
            if (source.thread == null || source.finished) required++;
            if (required > config.maxActiveInstances) {
                linkResult(offer, LinkPackets.UNAVAILABLE);
                linkNotice(player, "unavailable");
                linkNotice(senderPlayer, "unavailable");
                return;
            }
            GameLinkBridge bridge;
            try {
                bridge = GameLinkBridge.open(Components.LINK_PORTS[0], Components.LINK_PORTS[1]);
            } catch (IOException ex) {
                linkResult(offer, LinkPackets.UNAVAILABLE);
                linkNotice(player, "unavailable");
                return;
            }
            int port = bridge.port();
            sender.linkBridge = source.linkBridge = bridge;
            sender.linkPort = source.linkPort = port;
            sender.linkPartner = source;
            source.linkPartner = sender;
            sender.linkOptions = Components.gambatteLink(false, port);
            source.linkOptions = Components.gambatteLink(false, port);
            // A peer without a cable can be running locally; return its checkpoint first.
            if (source.ownership.mode() == Ownership.Mode.CLIENT) returnOwnership(source);
            else if (!source.ownership.serverMayRun()) revoke(source, false);
            notifyLink(sender, LinkPackets.CONNECTED);
            notifyLink(source, LinkPackets.CONNECTED);
            linkNotice(senderPlayer, "connected");
            linkNotice(player, "connected");
            return;
        }
        if (packet.action() == LinkPackets.LIST) {
            long last = lastLinkRequest.getOrDefault(player.getUUID(), 0L);
            if (now - last < TimeUnit.MILLISECONDS.toNanos(500)) return;
            lastLinkRequest.put(player.getUUID(), now);
        }
        if (source.linkPartner != null) {
            notifyLink(source, LinkPackets.CONNECTED);
            return;
        }
        if (!source.linked) {
            linkNotice(player, "cable_required");
            return;
        }
        if (Boolean.FALSE.equals(source.linkSupported)) {
            linkNotice(player, "unsupported");
            return;
        }
        if (packet.action() == LinkPackets.LIST) {
            List<LinkPackets.Entry> entries = new ArrayList<>();
            for (Session other : sessions.values()) {
                var target = linkPlayer(other);
                if (target != null
                        && target != player
                        && linkNearby(source, other)
                        && other.linkPartner == null
                        && !linkInvitations.busy(other.data.id())
                        && !Boolean.FALSE.equals(other.linkSupported))
                    entries.add(
                            new LinkPackets.Entry(
                                    other.data.id(),
                                    com.sammwy.retrocraft.compat.Profiles.name(
                                            target.getGameProfile())));
            }
            entries.sort(Comparator.comparing(LinkPackets.Entry::name));
            send(
                    player,
                    new LinkPackets.State(
                            LinkPackets.CANDIDATES,
                            source.data.id(),
                            LinkPackets.NONE,
                            List.copyOf(entries.subList(0, Math.min(32, entries.size())))));
        } else if (packet.action() == LinkPackets.INVITE) {
            Session target = sessions.get(packet.target());
            var recipient = linkPlayer(target);
            if (recipient == null
                    || !linkNearby(source, target)
                    || target.linkPartner != null
                    || Boolean.FALSE.equals(target.linkSupported)) {
                send(
                        player,
                        new LinkPackets.State(
                                LinkPackets.UNAVAILABLE,
                                source.data.id(),
                                LinkPackets.NONE,
                                List.of()));
                linkNotice(player, "unavailable");
                return;
            }
            var offer =
                    linkInvitations.invite(
                            source.data.id(),
                            target.data.id(),
                            player.getUUID(),
                            recipient.getUUID(),
                            now);
            if (offer == null) {
                send(
                        player,
                        new LinkPackets.State(
                                LinkPackets.UNAVAILABLE,
                                source.data.id(),
                                LinkPackets.NONE,
                                List.of()));
                linkNotice(player, "busy");
                return;
            }
            send(
                    recipient,
                    new LinkPackets.State(
                            LinkPackets.OFFER,
                            target.data.id(),
                            offer.token(),
                            List.of(
                                    new LinkPackets.Entry(
                                            source.data.id(),
                                            com.sammwy.retrocraft.compat.Profiles.name(
                                                    player.getGameProfile())))));
            send(
                    player,
                    new LinkPackets.State(
                            LinkPackets.WAITING,
                            source.data.id(),
                            offer.token(),
                            List.of(
                                    new LinkPackets.Entry(
                                            target.data.id(),
                                            com.sammwy.retrocraft.compat.Profiles.name(
                                                    recipient.getGameProfile())))));
            linkNotice(player, "sent");
        }
    }

    private void notifyLink(Session session, int mode) {
        var player = linkPlayer(session);
        var partner = linkPlayer(session.linkPartner);
        List<LinkPackets.Entry> entries =
                partner == null
                        ? List.of()
                        : List.of(
                                new LinkPackets.Entry(
                                        session.linkPartner.data.id(),
                                        com.sammwy.retrocraft.compat.Profiles.name(
                                                partner.getGameProfile())));
        if (player != null)
            send(player, new LinkPackets.State(mode, session.data.id(), LinkPackets.NONE, entries));
    }

    private void linkResult(LinkInvitations.Offer offer, int mode) {
        var sender = server.getPlayerList().getPlayer(offer.sender());
        var recipient = server.getPlayerList().getPlayer(offer.recipient());
        if (sender != null)
            send(
                    sender,
                    new LinkPackets.State(
                            mode,
                            offer.source(),
                            offer.token(),
                            recipient == null
                                    ? List.of()
                                    : List.of(
                                            new LinkPackets.Entry(
                                                    offer.target(),
                                                    com.sammwy.retrocraft.compat.Profiles.name(
                                                            recipient.getGameProfile())))));
    }

    private void closeOffer(LinkInvitations.Offer offer) {
        var recipient = server.getPlayerList().getPlayer(offer.recipient());
        if (recipient != null)
            send(
                    recipient,
                    new LinkPackets.State(
                            LinkPackets.CLOSED, offer.target(), offer.token(), List.of()));
    }

    private void disconnectLink(Session session) {
        Session partner = session.linkPartner;
        if (session.linkBridge != null) session.linkBridge.close();
        session.linkBridge = null;
        session.linkPartner = null;
        session.linkPort = -1;
        session.linkOptions = HardwareProfile.options("gambatte_gb_link_mode", "Not Connected");
        notifyLink(session, LinkPackets.CLOSED);
        if (partner != null) {
            partner.linkBridge = null;
            partner.linkPartner = null;
            partner.linkPort = -1;
            partner.linkOptions = HardwareProfile.options("gambatte_gb_link_mode", "Not Connected");
            notifyLink(partner, LinkPackets.CLOSED);
            linkNotice(linkPlayer(partner), "disconnected");
            linkNotice(linkPlayer(session), "disconnected");
        }
    }

    private void tickLinks() {
        long now = System.nanoTime();
        for (var offer : linkInvitations.offers()) {
            Session a = sessions.get(offer.source()), b = sessions.get(offer.target());
            var sender = linkPlayer(a);
            var recipient = linkPlayer(b);
            if (now >= offer.expires()
                    || !linkNearby(a, b)
                    || sender == null
                    || recipient == null
                    || !sender.getUUID().equals(offer.sender())
                    || !recipient.getUUID().equals(offer.recipient())) {
                linkInvitations.remove(offer);
                closeOffer(offer);
                linkResult(offer, LinkPackets.EXPIRED);
                linkNotice(sender, "expired");
            }
        }
        for (Session a : sessions.values()) {
            Session b = a.linkPartner;
            if (b == null) continue;
            if (a.linkBridge == null
                    || a.linkBridge.closed()
                    || !linkNearby(a, b)
                    || linkPlayer(a) == null
                    || linkPlayer(b) == null
                    || Boolean.FALSE.equals(a.linkSupported)
                    || Boolean.FALSE.equals(b.linkSupported)) disconnectLink(a);
        }
    }

    /** Each console's game system declares the firmware it needs. */
    private static void requireBios(String coreId, RomIndex.Game game) throws IOException {
        game.cartridge().system().bios().require(coreId, game);
    }

    private void finish(Session session) {
        if (!session.finished) return;
        if (session.linkPartner != null) disconnectLink(session);
        session.thread = null;
        session.finished = false;
        resetVideo(session);
        if (session.failure != null) {
            for (UUID id : session.videoWatchers.keySet()) {
                var player = server.getPlayerList().getPlayer(id);
                if (player != null) error(player, session.failure, session);
            }
            stopSubscribers(session);
        }
    }

    private static String failureKey(Throwable failure) {
        if (failure instanceof RomRejectedException) return "rom_rejected";
        if (failure instanceof BiosFiles.MissingException) return "bios_missing";
        if (failure instanceof IOException && failure.getMessage() != null) {
            if (failure.getMessage().startsWith("PS2 BIOS missing")) return "bios_missing";
            if (failure.getMessage().startsWith("ROM/core changed since snapshot"))
                return "rom_changed";
        }
        return "failed";
    }

    private void stopSubscribers(Session session) {
        for (UUID id : List.copyOf(session.videoWatchers.keySet())) {
            var player = server.getPlayerList().getPlayer(id);
            if (player != null) send(player, stopped(session));
            detachVideo(id);
        }
        session.videoWatchers.clear();
        for (InputSource pad : session.pads) if (pad != null) releaseInput(pad.player);
        for (UUID id : session.audioWatchers.keySet()) {
            var player = server.getPlayerList().getPlayer(id);
            if (player != null) send(player, new Packets.AudioStop(session.data.id()));
        }
        session.audioWatchers.clear();
    }

    private void notifyRoles(Session session) {
        for (UUID id : session.videoWatchers.keySet()) {
            var player = server.getPlayerList().getPlayer(id);
            if (player != null) send(player, status(session, player, Packets.ROLE));
        }
    }

    /** Players with the play screen open; ambient television watchers get no HUD traffic. */
    private void forHudWatchers(Session session, Consumer<ServerPlayer> action) {
        for (var entry : session.videoWatchers.entrySet()) {
            if (entry.getValue().ambient) continue;
            var player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) action.accept(player);
        }
    }

    private void notifyPlayers(Session session) {
        List<Packets.Players.Entry> entries = new ArrayList<>(session.pads.length);
        UUID owner = session.ownership.owner();
        boolean owned = session.ownership.mode() == Ownership.Mode.CLIENT;
        for (int slot = 0; slot < session.pads.length; slot++)
            if (session.pads[slot] != null)
                entries.add(
                        new Packets.Players.Entry(
                                slot,
                                session.pads[slot].player,
                                owned && session.pads[slot].player.equals(owner)));
        forHudWatchers(
                session,
                player -> {
                    send(player, new Packets.Players(session.data.id(), entries));
                    for (int port = 0; port < session.pads.length; port++)
                        if (session.pads[port] != null)
                            send(
                                    player,
                                    new Packets.Pad(
                                            session.data.id(), port, session.pads[port].state));
                });
    }

    private void broadcastPad(Session session, int slot, PadInput state) {
        forHudWatchers(
                session, player -> send(player, new Packets.Pad(session.data.id(), slot, state)));
    }

    private Packets.Ui status(Session session, ServerPlayer player, int mode) {
        long screen = 0;
        if (session.host instanceof DockedConsoleBlockEntity console)
            screen = (console.tv() != null ? console.tv() : console.getBlockPos()).asLong();
        return new Packets.Ui(
                mode,
                session.data.id(),
                session.host.definition().id().toString(),
                session.game.name(),
                isInputSource(player.getUUID(), session.data.id()),
                session.host.canAccess(player),
                inWorld(session),
                screen,
                session.hardware.aims());
    }

    private static Packets.Ui stopped(Session session) {
        return new Packets.Ui(
                Packets.STOP, session.data.id(), "", "", false, false, false, 0, false);
    }

    private void destroyFiles(UUID id) {
        try {
            store.destroy(id);
        } catch (IOException ex) {
            Retrocraft.LOG.error("Cannot destroy console instance {}", id, ex);
        }
    }

    private void destroySession(Session session) {
        if (!session.cleanupStarted.compareAndSet(false, true)) return;
        while (session.checkpointWrites.get() > 0)
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(25));
        persistSave(session);
        destroyFiles(session.data.id());
    }

    /**
     * Copies the durable per-game save into a freshly created instance so powered consoles resume
     * where they left off.
     */
    private void restoreSave(InstanceStore.Metadata data) throws IOException {
        if (config.saveMode == RetrocraftConfig.SaveMode.NONE) return;
        byte[] battery =
                saves.restoreBattery(
                        data.owner(),
                        config.saveMode == RetrocraftConfig.SaveMode.GLOBAL,
                        data.gameId());
        if (battery != null)
            SaveStates.writeAtomic(store.directory(data.id()).resolve("battery.sav"), battery);
    }

    /**
     * Savestates older than a week without being loaded are orphaned; battery saves are never
     * touched.
     */
    private void cleanOrphanSaves() {
        int removed = saves.cleanOrphans(Duration.ofDays(7));
        if (removed > 0) Retrocraft.LOG.info("Removed {} orphaned savestate(s)", removed);
    }

    /**
     * The session is going away: publish its latest snapshot to the durable save directory. A clean
     * shutdown deletes the savestate (it is the powered console's RAM only); the battery save
     * stays.
     */
    private void persistSave(Session session) {
        if (config.saveMode == RetrocraftConfig.SaveMode.NONE) return;
        try {
            boolean global = config.saveMode == RetrocraftConfig.SaveMode.GLOBAL;
            Path battery = store.directory(session.data.id()).resolve("battery.sav");
            if (Files.isRegularFile(battery))
                saves.persistBattery(session.data.owner(), global, session.data.gameId(), battery);
            if (!session.poweredOff || !Files.isRegularFile(battery))
                saves.persist(
                        session.data.owner(),
                        global,
                        session.data.gameId(),
                        store.snapshot(session.data.id()),
                        !session.poweredOff);
        } catch (IOException ex) {
            Retrocraft.LOG.warn("Cannot persist save for {}", session.data.id());
        }
    }

    private static void send(ServerPlayer player, CustomPacketPayload packet) {
        Network.send(player, packet);
    }

    private static void error(ServerPlayer player, Throwable failure, Session session) {
        if (failure instanceof BiosFiles.MissingException missing) {
            boolean admin = operator(player);
            var arcadeBios = missing.biosId() != null ? ArcadeSets.bios(missing.biosId()) : null;
            if (arcadeBios != null) {
                player.displayClientMessage(
                        Component.translatable(
                                "message.retrocraft.arcade_bios_missing" + (admin ? "_admin" : ""),
                                arcadeBios.zip(),
                                arcadeBios.title()),
                        false);
            } else {
                player.displayClientMessage(
                        Component.translatable(
                                "message.retrocraft.bios_missing" + (admin ? "_admin" : "")),
                        false);
            }
            if (Network.canSend(player, SettingsPackets.BiosRequired.ID))
                send(player, new SettingsPackets.BiosRequired(missing.packetTarget(), admin));
            return;
        }
        if (failure instanceof RomRejectedException rejected && session != null) {
            var machine = ArcadeSets.machine(session.game.filename());
            if (machine != null) {
                Path romDir = rejected.rom != null ? rejected.rom.getParent() : null;
                if (machine.parent() != null
                        && romDir != null
                        && !Files.isRegularFile(romDir.resolve(machine.parent() + ".zip"))) {
                    player.displayClientMessage(
                            Component.translatable(
                                    "message.retrocraft.rom_rejected_parent",
                                    machine.zip(),
                                    machine.title(),
                                    machine.parent() + ".zip",
                                    ArcadeSets.title(machine.parent())),
                            false);
                    return;
                }
                player.displayClientMessage(
                        Component.translatable(
                                "message.retrocraft.rom_rejected_mame",
                                machine.zip(),
                                machine.title()),
                        false);
                return;
            }
        }
        error(player, failureKey(failure));
    }

    private static void error(ServerPlayer player, String key) {
        if (key.equals("bios_missing")) {
            boolean admin = operator(player);
            player.displayClientMessage(
                    Component.translatable(
                            "message.retrocraft.bios_missing" + (admin ? "_admin" : "")),
                    false);
            if (Network.canSend(player, SettingsPackets.BiosRequired.ID))
                send(player, new SettingsPackets.BiosRequired("pcsx2", admin));
            return;
        }
        player.displayClientMessage(Component.translatable("message.retrocraft." + key), false);
    }

    private void sendCoreSettings(ServerPlayer player) {
        if (roms == null
                || !preparedCores.keySet().containsAll(requiredCores)
                || !Network.canSend(player, HybridPackets.Settings.ID)) return;
        Integer previous = clientGenerations.put(player.getUUID(), coreGeneration);
        if (previous == null || previous != coreGeneration) {
            readyClients.remove(player.getUUID());
            verifiedClients.remove(player.getUUID());
            biosReadyClients.remove(player.getUUID());
        }
        send(
                player,
                new HybridPackets.Settings(
                        coreGeneration,
                        KnownCores.POOL,
                        config.checkpointIntervalSeconds,
                        config.streamFps,
                        config.thumbnailIntervalSeconds,
                        config.thumbnailUpload,
                        config.emulationMode.ordinal(),
                        requiredCores.stream()
                                .sorted()
                                .map(id -> preparedCores.get(id).identity())
                                .toList()));
        sendBiosManifest(player);
    }

    private void sendBiosManifest(ServerPlayer player) {
        if (!Network.canSend(player, SettingsPackets.BiosManifest.ID)
                || !biosManifestSent.add(player.getUUID())) return;
        UUID playerId = player.getUUID();
        int generation = coreGeneration;
        Set<String> cores = Set.copyOf(requiredCores);
        Thread.ofVirtual()
                .name("retrocraft-bios-manifest")
                .start(
                        () -> {
                            List<SettingsPackets.BiosManifest.Entry> entries = new ArrayList<>();
                            for (String id : cores) {
                                if (!KnownCores.get(id).needsBios()) continue;
                                String directory = BiosFiles.directory(id);
                                if (!Files.isDirectory(
                                        RetrocraftConfig.root().resolve("bios").resolve(directory)))
                                    continue;
                                try {
                                    for (var file : biosLibrary.list(directory).entries()) {
                                        if (file.directory()
                                                || !RomLibrary.safeName(file.name())
                                                || file.size() < 1
                                                || file.size() > 8L * 1024 * 1024) continue;
                                        Path path = biosLibrary.read(directory + "/" + file.name());
                                        entries.add(
                                                new SettingsPackets.BiosManifest.Entry(
                                                        id,
                                                        file.name(),
                                                        file.size(),
                                                        FileHashes.sha256(path)));
                                    }
                                } catch (IOException ex) {
                                    Retrocraft.LOG.warn("Cannot list BIOS for {}", id, ex);
                                }
                            }
                            callbacks.add(
                                    () -> {
                                        ServerPlayer current =
                                                server.getPlayerList().getPlayer(playerId);
                                        if (current == null
                                                || !biosManifestSent.contains(playerId)
                                                || !requiredCores.equals(cores)
                                                || coreGeneration != generation) return;
                                        for (int i = 0; i < entries.size(); i += 128)
                                            send(
                                                    current,
                                                    new SettingsPackets.BiosManifest(
                                                            generation,
                                                            i == 0,
                                                            i + 128 >= entries.size(),
                                                            entries.subList(
                                                                    i,
                                                                    Math.min(
                                                                            i + 128,
                                                                            entries.size()))));
                                        if (entries.isEmpty())
                                            send(
                                                    current,
                                                    new SettingsPackets.BiosManifest(
                                                            generation, true, true, List.of()));
                                    });
                        });
    }

    public void biosReady(ServerPlayer player, SettingsPackets.BiosReady packet) {
        if (!Objects.equals(clientGenerations.get(player.getUUID()), packet.generation())) return;
        biosReadyClients.add(player.getUUID());
        markClientReady(player);
    }

    private void markClientReady(ServerPlayer player) {
        if (!verifiedClients.contains(player.getUUID())
                || !biosReadyClients.contains(player.getUUID())
                || !readyClients.add(player.getUUID())) return;
        Retrocraft.LOG.info(
                "Client {} ready for hybrid emulation: {}",
                com.sammwy.retrocraft.compat.Profiles.name(player.getGameProfile()),
                requiredCores);
    }

    public void requestBios(ServerPlayer player, SettingsPackets.BiosRequest request) {
        if (!clientGenerations.containsKey(player.getUUID())
                || !requiredCores.contains(request.coreId())
                || !KnownCores.get(request.coreId()).needsBios()
                || !RomLibrary.safeName(request.name())
                || !request.sha256().matches("[0-9a-f]{64}")) return;
        UUID playerId = player.getUUID();
        Set<String> pending = biosPending.computeIfAbsent(playerId, ignored -> new HashSet<>());
        String key = request.coreId() + "/" + request.name() + "/" + request.sha256();
        if (pending.size() >= 2 || !pending.add(key)) return;
        Thread.ofVirtual()
                .name("retrocraft-bios-read")
                .start(
                        () -> {
                            try {
                                Path path =
                                        biosLibrary.read(
                                                BiosFiles.directory(request.coreId())
                                                        + "/"
                                                        + request.name());
                                if (Files.size(path) < 1 || Files.size(path) > 8L * 1024 * 1024)
                                    throw new IOException("Invalid BIOS size");
                                byte[] bytes = Files.readAllBytes(path);
                                if (!BlobTransfer.hash(bytes).equals(request.sha256()))
                                    throw new IOException("BIOS changed after manifest");
                                callbacks.add(
                                        () -> {
                                            if (biosPending
                                                    .getOrDefault(playerId, Set.of())
                                                    .contains(key))
                                                biosOutgoing
                                                        .computeIfAbsent(
                                                                playerId,
                                                                ignored -> new ArrayDeque<>())
                                                        .add(
                                                                new BiosOutgoing(
                                                                        request.coreId(),
                                                                        request.name(),
                                                                        request.sha256(),
                                                                        bytes));
                                        });
                            } catch (IOException ex) {
                                Retrocraft.LOG.warn(
                                        "Cannot send BIOS {} for {}",
                                        request.name(),
                                        request.coreId(),
                                        ex);
                                callbacks.add(
                                        () -> {
                                            biosPending
                                                    .getOrDefault(playerId, Set.of())
                                                    .remove(key);
                                            ServerPlayer current =
                                                    server.getPlayerList().getPlayer(playerId);
                                            if (current != null
                                                    && Network.canSend(
                                                            current,
                                                            SettingsPackets.BiosUnavailable.ID))
                                                send(
                                                        current,
                                                        new SettingsPackets.BiosUnavailable(
                                                                request.coreId(),
                                                                request.name(),
                                                                request.sha256()));
                                        });
                            }
                        });
    }

    private void flushBios() {
        for (var entry : List.copyOf(biosOutgoing.entrySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || !Network.canSend(player, SettingsPackets.BiosChunk.ID)) continue;
            ArrayDeque<BiosOutgoing> queue = entry.getValue();
            for (int i = 0; i < 8 && !queue.isEmpty(); i++) {
                BiosOutgoing file = queue.peek();
                int end = Math.min(file.bytes.length, file.offset + BlobTransfer.CHUNK);
                send(
                        player,
                        new SettingsPackets.BiosChunk(
                                file.core,
                                file.name,
                                file.offset,
                                file.bytes.length,
                                file.sha,
                                Arrays.copyOfRange(file.bytes, file.offset, end)));
                file.offset = end;
                if (end == file.bytes.length) {
                    queue.removeFirst();
                    biosPending
                            .getOrDefault(entry.getKey(), Set.of())
                            .remove(file.core + "/" + file.name + "/" + file.sha);
                }
            }
            if (queue.isEmpty()) biosOutgoing.remove(entry.getKey());
        }
    }

    public void coreStatus(ServerPlayer player, HybridPackets.Status packet) {
        if (!Objects.equals(clientGenerations.get(player.getUUID()), packet.generation())) return;
        readyClients.remove(player.getUUID());
        verifiedClients.remove(player.getUUID());
        if (packet.phase() != HybridPackets.READY || packet.cores().size() != requiredCores.size())
            return;
        Map<String, CoreIdentity> reported = new HashMap<>();
        for (var identity : packet.cores())
            if (reported.put(identity.id(), identity) != null) return;
        for (String id : requiredCores)
            if (!preparedCores
                    .get(id)
                    .identity()
                    .compatible(
                            reported.get(id) == null
                                    ? new CoreIdentity("", "", "", "", "")
                                    : reported.get(id))) return;
        verifiedClients.add(player.getUUID());
        markClientReady(player);
    }

    private boolean controlsHere(Session session, ServerPlayer player) {
        InputSource first = session.pads[0];
        if (player == null
                || first == null
                || !first.player.equals(player.getUUID())
                || !session.host.canAccess(player)) return false;
        return session.data.id().equals(videoWatching.get(player.getUUID()));
    }

    private void coordinateOwnership(Session session, long now) {
        if (session.destroy || session.failure != null) return;
        if ((!clientMayEmulate() || serverOnly(session))
                && session.ownership.mode() == Ownership.Mode.CLIENT) returnOwnership(session);
        if (session.ownership.serverMayRun()
                && session.pads[0] != null
                && clientMayEmulate()
                && clientFits(session)) {
            var player = server.getPlayerList().getPlayer(session.pads[0].player);
            if (controlsHere(session, player)
                    && readyClients.contains(player.getUUID())
                    && !player.getUUID().equals(session.failedOffload)) {
                if (session.thread == null && !Files.exists(store.snapshot(session.data.id()))) {
                    if (activeCount() >= config.maxActiveInstances) return;
                    start(session); // initialize and serialize even before the first observed frame
                }
                session.ownership.offer(
                        player.getUUID(),
                        now,
                        TimeUnit.SECONDS.toNanos(config.transferTimeoutSeconds));
                Retrocraft.LOG.info(
                        "Instance {} preparing ownership epoch {} for {}",
                        session.data.id(),
                        session.ownership.epoch(),
                        com.sammwy.retrocraft.compat.Profiles.name(player.getGameProfile()));
                session.handoffStop = true;
                session.offerSent = false;
                session.romRequested = false;
                session.romReady = false;
                session.handoffStarted = now;
                session.romBytesSent = 0;
                session.media.clear();
                resetVideo(session);
                session.relayDecoder = new FrameCodec.Decoder();
                session.lastClientAudio = 0;
                session.lastClientVideo = 0;
            }
        }
        if (session.ownership.mode() != Ownership.Mode.PREPARING_CLIENT
                || session.thread != null
                || session.offerSent) return;
        UUID playerId = session.ownership.owner();
        long epoch = session.ownership.epoch();
        session.offerSent = true;
        Thread.ofVirtual()
                .name("retrocraft-handoff-read")
                .start(
                        () -> {
                            try {
                                byte[] snapshot =
                                        Files.readAllBytes(store.snapshot(session.data.id()));
                                SaveStates.validate(snapshot);
                                byte[] packed = SnapshotWire.pack(snapshot);
                                callbacks.add(
                                        () -> {
                                            if (!session.ownership.matches(playerId, epoch)
                                                    || session.ownership.mode()
                                                            != Ownership.Mode.PREPARING_CLIENT)
                                                return;
                                            var player = server.getPlayerList().getPlayer(playerId);
                                            if (!controlsHere(session, player)) {
                                                revoke(session, false);
                                                return;
                                            }
                                            long timeout =
                                                    BlobTransfer.timeoutSeconds(
                                                            config.transferTimeoutSeconds,
                                                            packed.length,
                                                            4);
                                            if (!session.ownership.preparationTime(
                                                    playerId,
                                                    epoch,
                                                    System.nanoTime(),
                                                    TimeUnit.SECONDS.toNanos(timeout))) {
                                                revoke(session, true);
                                                return;
                                            }
                                            session.checkpointBytes = packed.length;
                                            send(
                                                    player,
                                                    new HybridPackets.Lease(
                                                            session.data.id(),
                                                            epoch,
                                                            HybridPackets.Action.OFFER,
                                                            coreId(session.game),
                                                            session.data.gameId(),
                                                            session.game.name(),
                                                            session.game.filename(),
                                                            session.host
                                                                    .definition()
                                                                    .type()
                                                                    .isPlaced(),
                                                            0,
                                                            session.hardware.encode()));
                                            var queue =
                                                    outbound.computeIfAbsent(
                                                            playerId,
                                                            ignored -> new ArrayDeque<>());
                                            queue.add(
                                                    new BlobTransfer.Sender(
                                                            session.data.id(),
                                                            epoch,
                                                            BlobTransfer.Kind.SNAPSHOT,
                                                            0,
                                                            packed));
                                        });
                            } catch (Exception ex) {
                                callbacks.add(
                                        () -> {
                                            if (session.ownership.matches(playerId, epoch)) {
                                                Retrocraft.LOG.error(
                                                        "Cannot prepare ownership transfer", ex);
                                                revoke(session, true);
                                            }
                                        });
                            }
                        });
    }

    private void sendRom(Session session, UUID playerId, long epoch) {
        Thread.ofVirtual()
                .name("retrocraft-rom-download")
                .start(
                        () -> {
                            try {
                                byte[] rom = Files.readAllBytes(session.game.materialize());
                                if (!BlobTransfer.hash(rom).equals(session.data.gameId()))
                                    throw new IOException("ROM changed during handoff");
                                callbacks.add(
                                        () -> {
                                            if (!session.ownership.matches(playerId, epoch)
                                                    || session.ownership.mode()
                                                            != Ownership.Mode.PREPARING_CLIENT
                                                    || session.destroy) return;
                                            long budget =
                                                    BlobTransfer.timeoutSeconds(
                                                            config.transferTimeoutSeconds,
                                                            (long) rom.length
                                                                    + session.checkpointBytes,
                                                            4);
                                            if (!session.ownership.preparationTime(
                                                    playerId,
                                                    epoch,
                                                    System.nanoTime(),
                                                    TimeUnit.SECONDS.toNanos(budget))) {
                                                revoke(session, true);
                                                return;
                                            }
                                            outbound.computeIfAbsent(
                                                            playerId, ignored -> new ArrayDeque<>())
                                                    .add(
                                                            new BlobTransfer.Sender(
                                                                    session.data.id(),
                                                                    epoch,
                                                                    BlobTransfer.Kind.ROM,
                                                                    0,
                                                                    rom));
                                        });
                            } catch (Exception ex) {
                                callbacks.add(
                                        () -> {
                                            if (session.ownership.matches(playerId, epoch)) {
                                                Retrocraft.LOG.error(
                                                        "Cannot supply requested ROM", ex);
                                                revoke(session, true);
                                            }
                                        });
                            }
                        });
    }

    public void ownership(ServerPlayer player, HybridPackets.Lease packet) {
        Session session = sessions.get(packet.instance());
        if (session == null
                || session.destroy
                || !session.ownership.matches(player.getUUID(), packet.epoch())) return;
        long now = System.nanoTime();
        switch (packet.action()) {
            case ROM_REQUEST -> {
                if (session.ownership.mode() != Ownership.Mode.PREPARING_CLIENT
                        || !session.offerSent
                        || session.romRequested
                        || session.romReady
                        || !controlsHere(session, player)) return;
                session.romRequested = true;
                sendRom(session, player.getUUID(), packet.epoch());
            }
            case ROM_READY -> {
                if (session.ownership.mode() == Ownership.Mode.PREPARING_CLIENT
                        && session.offerSent) session.romReady = true;
            }
            case PREPARED -> {
                if (!readyClients.contains(player.getUUID())
                        || !controlsHere(session, player)
                        || session.thread != null
                        || !session.offerSent
                        || !session.romReady) return;
                if (session.ownership.commit(
                        player.getUUID(),
                        packet.epoch(),
                        now,
                        TimeUnit.SECONDS.toNanos(config.ownerTimeoutSeconds))) {
                    session.lastCheckpointAt = now;
                    Retrocraft.LOG.info(
                            "Instance {} committed client ownership epoch {} in {} ms (ROM cache {}, {} ROM bytes sent)",
                            session.data.id(),
                            packet.epoch(),
                            (now - session.handoffStarted) / 1_000_000,
                            session.romRequested ? "miss" : "hit",
                            session.romBytesSent);
                    session.media.clear();
                    resetVideo(session);
                    send(
                            player,
                            HybridPackets.Lease.signal(
                                    session.data.id(),
                                    packet.epoch(),
                                    HybridPackets.Action.COMMIT,
                                    0));
                    sendDemand(session);
                    send(player, new Packets.AudioStop(session.data.id()));
                    notifyPlayers(session);
                }
            }
            case HEARTBEAT -> {
                if (controlsHere(session, player))
                    session.ownership.heartbeat(
                            player.getUUID(),
                            packet.epoch(),
                            now,
                            TimeUnit.SECONDS.toNanos(config.ownerTimeoutSeconds));
            }
            case RETURN_BEGIN -> {
                if (session.ownership.beginReturn(
                        player.getUUID(), packet.epoch(), now, returnTimeout(session)))
                    releaseInput(player.getUUID());
            }
            case FAILED -> revoke(session, true);
            default -> {}
        }
    }

    private void sendDemand(Session session) {
        UUID owner = session.ownership.owner();
        if (owner == null || session.ownership.mode() != Ownership.Mode.CLIENT) return;
        boolean audio = session.audioWatchers.keySet().stream().anyMatch(id -> !id.equals(owner));
        boolean video = session.videoWatchers.keySet().stream().anyMatch(id -> !id.equals(owner));
        AudioWatcher listener = session.audioWatchers.get(owner);
        float gain = listener == null ? 0 : listener.gain;
        if (session.demandEpoch == session.ownership.epoch()
                && audio == session.relayAudio
                && video == session.relayVideo
                && Math.abs(gain - session.ownerGain) < .02f) return;
        var player = server.getPlayerList().getPlayer(owner);
        if (player == null) return;
        send(
                player,
                new HybridPackets.Demand(
                        session.data.id(), session.ownership.epoch(), audio, video, gain));
        session.demandEpoch = session.ownership.epoch();
        session.relayAudio = audio;
        session.relayVideo = video;
        session.ownerGain = gain;
    }

    private long returnTimeout(Session session) {
        return TimeUnit.SECONDS.toNanos(
                BlobTransfer.timeoutSeconds(
                        config.transferTimeoutSeconds, session.checkpointBytes, 2));
    }

    private void returnOwnership(Session session) {
        UUID playerId = session.ownership.owner();
        if (playerId == null) return;
        if (session.ownership.mode() == Ownership.Mode.PREPARING_CLIENT) {
            revoke(session, false);
            return;
        }
        if (session.ownership.beginReturn(
                playerId, session.ownership.epoch(), System.nanoTime(), returnTimeout(session))) {
            var player = server.getPlayerList().getPlayer(playerId);
            if (player == null) revoke(session, false);
            else
                send(
                        player,
                        HybridPackets.Lease.signal(
                                session.data.id(),
                                session.ownership.epoch(),
                                HybridPackets.Action.RETURN_REQUEST,
                                0));
        }
    }

    private void revoke(Session session, boolean failed) {
        UUID playerId = session.ownership.owner();
        long epoch = session.ownership.epoch();
        if (playerId != null) {
            var player = server.getPlayerList().getPlayer(playerId);
            if (player != null)
                send(
                        player,
                        HybridPackets.Lease.signal(
                                session.data.id(), epoch, HybridPackets.Action.REVOKE, 0));
            var queue = outbound.get(playerId);
            if (queue != null) queue.removeIf(sender -> sender.belongsTo(session.data.id(), epoch));
            if (failed) session.failedOffload = playerId;
        }
        session.ownership.revoke();
        session.incoming = new ConcurrentHashMap<>();
        synchronized (session) {
            session.uploadQueue.clear();
        }
        session.media.clear();
        resetVideo(session);
        notifyPlayers(session);
        Retrocraft.LOG.info(
                "Instance {} returned to server at epoch {}{}",
                session.data.id(),
                session.ownership.epoch(),
                failed ? " (fallback checkpoint)" : "");
    }

    private void flushTransfers() {
        for (UUID playerId : List.copyOf(outbound.keySet())) {
            var player = server.getPlayerList().getPlayer(playerId);
            var queue = outbound.get(playerId);
            if (player == null) {
                outbound.remove(playerId);
                continue;
            }
            for (int i = 0; i < 4 && !queue.isEmpty(); i++) {
                var sender = queue.getFirst();
                var chunk = sender.next();
                send(player, new HybridPackets.Blob(chunk));
                if (chunk.kind() == BlobTransfer.Kind.ROM) {
                    Session session = sessions.get(chunk.instance());
                    if (session != null && session.ownership.matches(playerId, chunk.epoch()))
                        session.romBytesSent += chunk.bytes().length;
                }
                if (sender.done()) queue.removeFirst();
            }
            if (queue.isEmpty()) outbound.remove(playerId);
        }
    }

    public void upload(ServerPlayer player, HybridPackets.Blob packet) {
        var chunk = packet.chunk();
        Session session = sessions.get(chunk.instance());
        if (session == null
                || session.destroy
                || !session.ownership.matches(player.getUUID(), chunk.epoch())) return;
        boolean state =
                chunk.kind() == BlobTransfer.Kind.SNAPSHOT
                        || chunk.kind() == BlobTransfer.Kind.FINAL_SNAPSHOT;
        if (chunk.kind() == BlobTransfer.Kind.ROM
                || (!state && !session.ownership.clientMayRun(player.getUUID(), chunk.epoch()))
                || state
                        && !session.ownership.acceptsCheckpoint(
                                player.getUUID(), chunk.epoch(), chunk.sequence())) return;
        if (state && chunk.total() > 0 && chunk.total() <= BlobTransfer.limit(chunk.kind()))
            session.checkpointBytes = Math.max(session.checkpointBytes, chunk.total());
        session.lastIncomingChunk = System.nanoTime();
        synchronized (session) {
            if (session.uploadQueue.size() >= 128) {
                revoke(session, true);
                return;
            }
            session.uploadQueue.addLast(new UploadChunk(player.getUUID(), chunk));
            if (session.decodingUpload) return;
            session.decodingUpload = true;
        }
        Thread.ofVirtual().name("retrocraft-owner-decode").start(() -> decodeUpload(session));
    }

    private void decodeUpload(Session session) {
        while (true) {
            UploadChunk upload;
            synchronized (session) {
                upload = session.uploadQueue.pollFirst();
                if (upload == null) {
                    session.decodingUpload = false;
                    return;
                }
            }
            var chunk = upload.chunk();
            if (stopping || session.destroy) {
                synchronized (session) {
                    session.decodingUpload = false;
                    session.uploadQueue.clear();
                }
                return;
            }
            if (!session.ownership.matches(upload.player(), chunk.epoch())) continue;
            boolean state =
                    chunk.kind() == BlobTransfer.Kind.SNAPSHOT
                            || chunk.kind() == BlobTransfer.Kind.FINAL_SNAPSHOT;
            try {
                Map<BlobTransfer.Kind, BlobTransfer.Receiver> incoming = session.incoming;
                incoming.values().removeIf(receiver -> receiver.expired(System.nanoTime()));
                var receiver =
                        incoming.computeIfAbsent(
                                chunk.kind(), ignored -> new BlobTransfer.Receiver());
                if (receiver.reserved() == 0
                        && incoming.values().stream()
                                                .mapToLong(BlobTransfer.Receiver::reserved)
                                                .sum()
                                        + chunk.total()
                                > BlobTransfer.MAX_SNAPSHOT + 2_000_000L)
                    throw new IOException("Owner upload budget exceeded");
                byte[] complete = receiver.accept(chunk, System.nanoTime());
                if (complete == null) continue;
                if (state)
                    callbacks.add(
                            () -> {
                                if (!session.ownership.matches(upload.player(), chunk.epoch()))
                                    return;
                                session.checkpointBytes =
                                        Math.max(session.checkpointBytes, chunk.total());
                                try {
                                    checkpoint(session, upload.player(), chunk, complete);
                                } catch (IOException ex) {
                                    Retrocraft.LOG.warn("Rejected owner checkpoint", ex);
                                    revoke(session, true);
                                }
                            });
                else if (chunk.kind() == BlobTransfer.Kind.VIDEO) {
                    FrameCodec.Decoder decoder = session.relayDecoder;
                    var frame = decoder.decode(MediaWire.video(complete));
                    callbacks.add(
                            () -> {
                                if (!session.ownership.matches(upload.player(), chunk.epoch())
                                        || decoder != session.relayDecoder
                                        || chunk.sequence() <= session.lastClientVideo) return;
                                var player = server.getPlayerList().getPlayer(upload.player());
                                if (frame == null) {
                                    if (player != null)
                                        send(
                                                player,
                                                HybridPackets.Lease.signal(
                                                        session.data.id(),
                                                        chunk.epoch(),
                                                        HybridPackets.Action.REQUEST_KEYFRAME,
                                                        0));
                                } else {
                                    session.lastClientVideo = chunk.sequence();
                                    session.media.offer(
                                            new WorkerProcess.Output(frame, new byte[0]),
                                            session.sampleRate);
                                }
                            });
                } else if (chunk.kind() == BlobTransfer.Kind.AUDIO) {
                    var audio = MediaWire.audio(complete);
                    callbacks.add(
                            () -> {
                                if (!session.ownership.matches(upload.player(), chunk.epoch())
                                        || chunk.sequence() <= session.lastClientAudio) return;
                                session.sampleRate = audio.rate();
                                session.lastClientAudio = chunk.sequence();
                                session.media.offer(
                                        new WorkerProcess.Output(null, audio.pcm()), audio.rate());
                            });
                }
            } catch (IOException | RuntimeException ex) {
                callbacks.add(
                        () -> {
                            if (session.ownership.matches(upload.player(), chunk.epoch())) {
                                Retrocraft.LOG.warn("Rejected owner upload: {}", ex.toString());
                                revoke(session, true);
                            }
                        });
                synchronized (session) {
                    session.decodingUpload = false;
                    session.uploadQueue.clear();
                }
                return;
            }
        }
    }

    private void checkpoint(Session session, UUID player, BlobTransfer.Chunk chunk, byte[] bytes)
            throws IOException {
        String writeId = chunk.epoch() + ":" + chunk.sequence();
        if (session.pendingCheckpointWrites.size() >= 2
                || !session.pendingCheckpointWrites.add(writeId))
            throw new IOException("Checkpoint already pending or write budget exhausted");
        Path staged =
                store.directory(session.data.id())
                        .resolve("received-" + chunk.epoch() + "-" + chunk.sequence() + ".bin");
        session.checkpointWrites.incrementAndGet();
        Thread.ofVirtual()
                .name("retrocraft-checkpoint-write")
                .start(
                        () -> {
                            try {
                                byte[] state = SnapshotWire.unpack(bytes);
                                SaveStates.validate(state);
                                SaveStates.writeAtomic(staged, state);
                                if (stopping || session.destroy) {
                                    Files.deleteIfExists(staged);
                                    return;
                                }
                                callbacks.add(
                                        () -> {
                                            session.pendingCheckpointWrites.remove(writeId);
                                            try {
                                                if (session.destroy
                                                        || !session.ownership.acceptsCheckpoint(
                                                                player,
                                                                chunk.epoch(),
                                                                chunk.sequence())) {
                                                    Files.deleteIfExists(staged);
                                                    return;
                                                }
                                                store.promote(session.data.id(), staged);
                                                session.ownership.checkpoint(
                                                        player, chunk.epoch(), chunk.sequence());
                                                session.lastCheckpointAt = System.nanoTime();
                                                var target =
                                                        server.getPlayerList().getPlayer(player);
                                                if (target != null)
                                                    send(
                                                            target,
                                                            HybridPackets.Lease.signal(
                                                                    session.data.id(),
                                                                    chunk.epoch(),
                                                                    HybridPackets.Action
                                                                            .CHECKPOINT_ACK,
                                                                    chunk.sequence()));
                                                if (chunk.kind()
                                                        == BlobTransfer.Kind.FINAL_SNAPSHOT) {
                                                    // A newly opened HUD may already have claimed
                                                    // input during the return.
                                                    revoke(session, false);
                                                    notifyRoles(session);
                                                }
                                            } catch (IOException ex) {
                                                Retrocraft.LOG.error(
                                                        "Cannot commit checkpoint", ex);
                                                revoke(session, true);
                                            }
                                        });
                            } catch (IOException ex) {
                                callbacks.add(
                                        () -> {
                                            session.pendingCheckpointWrites.remove(writeId);
                                            Retrocraft.LOG.error(
                                                    "Cannot persist owner checkpoint", ex);
                                            if (session.ownership.matches(player, chunk.epoch()))
                                                revoke(session, true);
                                        });
                            } finally {
                                session.checkpointWrites.decrementAndGet();
                            }
                        });
    }

    @Override
    public void close() {
        stopping = true;
        for (Session session : sessions.values())
            if (session.linkBridge != null) session.linkBridge.close();
        libraryIo.getQueue().clear();
        libraryIo.shutdown();
        for (PendingStart pending : pendingStarts.values())
            if (pending.created != null) {
                UUID id = pending.created;
                Thread.ofVirtual().name("retrocraft-cancel-power-on").start(() -> destroyFiles(id));
            }
        pendingStarts.clear();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        for (Session session : sessions.values()) {
            if (session.thread == null) continue;
            try {
                session.thread.join(
                        Math.max(1, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            if (session.thread.isAlive()) {
                Retrocraft.LOG.error("Console {} exceeded shutdown deadline", session.data.id());
                if (session.process != null) session.process.abort();
                session.thread.interrupt();
            }
        }
        for (Session session : sessions.values())
            if (!session.destroy && (session.thread == null || !session.thread.isAlive()))
                persistSave(session);
        droppedItems.clear();
        sessions.clear();
        videoWatching.clear();
        inputSessions.clear();
        failedDiscovery.clear();
        outbound.clear();
        readyClients.clear();
        callbacks.clear();
        romUploads.clear();
        downloads.clear();
        downloadNotified.clear();
    }
}
