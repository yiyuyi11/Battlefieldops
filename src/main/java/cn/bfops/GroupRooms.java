package cn.bfops;

import com.google.gson.Gson;
import io.netty.buffer.Unpooled;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Shared-directory discovery and one-use room handoff between trusted backends. */
final class GroupRooms implements AutoCloseable {
    static final long HEARTBEAT_TTL = 15_000;
    private static final long TICKET_TTL = 30_000;
    private static final int MAX_BYTES = 128 * 1024;
    private static final ResourceLocation CHANNEL = new ResourceLocation("bungeecord", "main");
    private static final Gson JSON = new Gson();
    private final Path root;
    private final String backend;
    private final List<String> peers;
    private final MinecraftServer server;
    private final ExecutorService io;
    private final AtomicBoolean refreshing = new AtomicBoolean();
    private final Map<UUID, Long> pending = new java.util.HashMap<>();
    private volatile List<Snapshot> cached = List.of();
    private volatile boolean closed;

    record Room(String map, String name, String mode, String modeName, String blueName, String redName,
                boolean running, int players, int capacity, int blue, int red, int waiting) {
        boolean valid() {
            return map != null && !map.isBlank() && map.length() <= 80
                    && safe(name) && safe(mode) && safe(modeName) && safe(blueName) && safe(redName)
                    && capacity > 0 && capacity <= 1024 && players >= 0
                    && blue >= 0 && red >= 0 && waiting >= 0;
        }
        private static boolean safe(String value) {return value != null && value.length() <= 128;}
    }
    record Snapshot(String backend, long written, List<Room> rooms) {}
    record Ticket(String source, String target, String map, UUID player, long written) {}

    static GroupRooms configured(MinecraftServer server) {
        if (!server.isDedicatedServer()) return null;
        String id = BattleServerMode.GROUP_BACKEND.get().strip();
        String directory = BattleServerMode.GROUP_DIRECTORY.get().strip();
        if (id.isEmpty() && directory.isEmpty()) return null;
        try {
            var peers = java.util.Arrays.stream(BattleServerMode.GROUP_PEERS.get().split(","))
                    .map(String::strip).filter(value -> !value.isEmpty() && !value.equals(id)).distinct().toList();
            Path root = Path.of(directory);
            if (!validBackend(id) || !root.isAbsolute() || peers.size() > 16 || peers.stream().anyMatch(peer -> !validBackend(peer)))
                throw new IllegalArgumentException("Expected an absolute directory and up to 16 valid backend IDs");
            return new GroupRooms(server, root.normalize(), id, peers);
        } catch (RuntimeException error) {
            BattlefieldOps.LOG.error("Battlefield group directory configuration is invalid", error);
            return null;
        }
    }

    private GroupRooms(MinecraftServer server, Path root, String backend, List<String> peers) {
        this.server = server;
        this.root = root;
        this.backend = backend;
        this.peers = peers;
        this.io = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "Battlefield room directory");
            thread.setDaemon(true);
            return thread;
        });
    }

    String backend() {return backend;}
    static boolean validBackend(String value) {return value != null && value.matches("[A-Za-z0-9_-]{1,48}");}
    static boolean fresh(long written, long now, long ttl) {return written <= now + 5000 && written >= now - ttl;}
    private Path snapshotPath(String id) {return root.resolve("rooms").resolve(id + ".json");}
    private Path inbox(String target) {return root.resolve("join").resolve(target);}
    private Path ticketPath(String target, UUID player) {return inbox(target).resolve(player + ".json");}

    void refresh(List<Room> local) {
        pending.values().removeIf(until -> until <= System.currentTimeMillis());
        if (closed || !refreshing.compareAndSet(false, true)) return;
        io.execute(() -> {
            try {
                long now = System.currentTimeMillis();
                write(snapshotPath(backend), new Snapshot(backend, now, local));
                List<Snapshot> next = new ArrayList<>();
                for (String peer : peers) {
                    try {
                        var snapshot = read(snapshotPath(peer), Snapshot.class);
                        if (snapshot != null && peer.equals(snapshot.backend) && snapshot.rooms != null
                                && fresh(snapshot.written, now, HEARTBEAT_TTL)) {
                            var rooms = snapshot.rooms.stream().filter(room -> room != null && room.valid()).limit(24).toList();
                            next.add(new Snapshot(peer, snapshot.written, rooms));
                        }
                    } catch (IOException | RuntimeException error) {
                        BattlefieldOps.LOG.debug("Cannot read room snapshot from {}", peer, error);
                    }
                }
                cached = List.copyOf(next);
                prune(now);
            } catch (IOException | RuntimeException error) {
                BattlefieldOps.LOG.debug("Cannot refresh Battlefield group rooms", error);
            } finally {
                refreshing.set(false);
            }
        });
    }

    List<Map<String, Object>> entries() {
        long now = System.currentTimeMillis();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (var snapshot : cached) {
            if (!fresh(snapshot.written, now, HEARTBEAT_TTL)) continue;
            for (var room : snapshot.rooms) {
                var row = new LinkedHashMap<String, Object>();
                row.put("id", "@" + snapshot.backend + "/" + room.map);
                row.put("backend", snapshot.backend);
                row.put("mapId", room.map);
                row.put("name", room.name);
                row.put("mode", room.mode);
                row.put("modeName", room.modeName);
                row.put("room", true);
                row.put("running", room.running);
                row.put("players", room.players);
                row.put("capacity", room.capacity);
                row.put("blue", room.blue);
                row.put("red", room.red);
                row.put("waiting", room.waiting);
                row.put("blueFactionName", room.blueName);
                row.put("redFactionName", room.redName);
                rows.add(row);
            }
        }
        return rows;
    }

    void connect(Engine engine, ServerPlayer player, String target, String map) {
        Model.require(!closed && peers.contains(target), "目标子服未配置");
        var room = cached.stream().filter(s -> s.backend.equals(target) && fresh(s.written, System.currentTimeMillis(), HEARTBEAT_TTL))
                .flatMap(s -> s.rooms.stream()).filter(r -> r.map.equals(map)).findFirst().orElse(null);
        Model.require(room != null && room.players < room.capacity, "远程房间已满或已离线，请刷新");
        UUID id = player.getUUID();
        long now = System.currentTimeMillis();
        Model.require(pending.getOrDefault(id, 0L) <= now, "已有跨服请求，请稍后再试");
        pending.put(id, now + TICKET_TTL);
        io.execute(() -> {
            try {
                if (!fresh(now, System.currentTimeMillis(), HEARTBEAT_TTL)) throw new IOException("Transfer request expired");
                write(ticketPath(target, id), new Ticket(backend, target, map, id, now));
                server.execute(() -> {
                    if (closed || server.getPlayerList().getPlayer(id) != player) return;
                    if (engine.canTransfer(player) && fresh(now, System.currentTimeMillis(), HEARTBEAT_TTL))
                        player.connection.send(new ClientboundCustomPayloadPacket(CHANNEL, connectPayload(target)));
                    else engine.groupTransferFailed(player);
                });
            } catch (IOException | RuntimeException error) {
                BattlefieldOps.LOG.debug("Cannot write group transfer ticket", error);
                server.execute(() -> {
                    pending.remove(id);
                    if (!closed && server.getPlayerList().getPlayer(id) == player) engine.groupTransferFailed(player);
                });
            }
        });
    }

    void arrived(Engine engine, ServerPlayer player) {
        UUID id = player.getUUID();
        if (closed) return;
        io.execute(() -> {
            try {
                Ticket request = claim(ticketPath(backend, id));
                if (validTicket(request, backend, peers, id, System.currentTimeMillis())) {
                    server.execute(() -> {
                        if (!closed && server.getPlayerList().getPlayer(id) == player
                                && fresh(request.written, System.currentTimeMillis(), TICKET_TTL))
                            engine.groupJoin(player, request.map);
                    });
                }
            } catch (IOException | RuntimeException error) {
                BattlefieldOps.LOG.debug("Cannot claim group transfer ticket", error);
            }
        });
    }

    static boolean validTicket(Ticket request, String target, List<String> peers, UUID player, long now) {
        return request != null && target.equals(request.target) && request.source != null && peers.contains(request.source)
                && player.equals(request.player) && request.map != null && !request.map.isBlank() && request.map.length() <= 80
                && fresh(request.written, now, TICKET_TTL);
    }

    static Ticket claim(Path ticket) throws IOException {
        Path claim = ticket.resolveSibling(UUID.randomUUID() + ".claim");
        try {
            Files.move(ticket, claim, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.NoSuchFileException absent) {
            return null;
        }
        try {return read(claim, Ticket.class);}
        finally {Files.deleteIfExists(claim);}
    }

    private void prune(long now) throws IOException {
        Path inbox = inbox(backend);
        if (!Files.isDirectory(inbox)) return;
        try (var files = Files.list(inbox)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json") || p.toString().endsWith(".claim")).limit(128).toList()) {
                try {if (now - Files.getLastModifiedTime(file).toMillis() > 120_000) Files.deleteIfExists(file);}
                catch (IOException ignored) {}
            }
        }
    }

    static FriendlyByteBuf connectPayload(String target) {
        try {
            var bytes = new ByteArrayOutputStream();
            try (var out = new DataOutputStream(bytes)) {out.writeUTF("Connect");out.writeUTF(target);}
            return new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes.toByteArray()));
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    static void write(Path path, Object value) throws IOException {
        byte[] data = JSON.toJson(value).getBytes(StandardCharsets.UTF_8);
        if (data.length > MAX_BYTES) throw new IOException("Group data exceeds size limit");
        Files.createDirectories(path.getParent());
        Path tmp = Files.createTempFile(path.getParent(), ".bfops-", ".tmp");
        try {
            Files.write(tmp, data);
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {Files.deleteIfExists(tmp);}
    }

    static <T> T read(Path path, Class<T> type) throws IOException {
        if (!Files.isRegularFile(path)) return null;
        try (var input = Files.newInputStream(path)) {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) throw new IOException("Group data exceeds size limit");
            return JSON.fromJson(new String(bytes, StandardCharsets.UTF_8), type);
        }
    }

    @Override public void close() {
        closed = true;
        io.shutdownNow();
    }
}
