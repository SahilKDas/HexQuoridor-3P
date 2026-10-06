// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2026 HexQuoridor-3P contributors
package hexquoridor;

import com.google.gson.*;
import org.java_websocket.WebSocket;
import java.util.*;

/** All room mutations are serialized. Membership, seat and revision are server-authoritative. */
public final class GameSessionManager {
    private static final Gson JSON=new Gson();
    private final Map<String,Room> rooms=new HashMap<>();
    private final Map<WebSocket,Member> members=new HashMap<>();
    private static final long EXPIRY=30*60*1000L;
    static final class Room {
        final String id, mode;
        final GameState game=new GameState();
        final String[] tokens=new String[3];
        final WebSocket[] seats=new WebSocket[3];
        final Set<WebSocket> connections=new HashSet<>();
        long touched=System.currentTimeMillis();
        Room(String id,String mode) { this.id=id; this.mode=mode; }
        boolean ready() { return mode.equals("local") || Arrays.stream(seats).allMatch(Objects::nonNull); }
    }
    record Member(Room room,int seat) {}
    public synchronized void message(WebSocket socket,String message) {
        try {
            if(message.length()>4096) throw new IllegalArgumentException("Message too large");
            JsonObject input=JsonParser.parseString(message).getAsJsonObject();
            String type=string(input,"type");
            if(type.equals("JOIN")) { join(socket,input); return; }
            Member member=members.get(socket);
            if(member==null) throw new IllegalArgumentException("Join a room first");
            Room room=member.room(); GameState game=room.game;
            if(!room.ready()) throw new IllegalArgumentException("Waiting for all three players");
            if(!room.mode.equals("local") && member.seat()!=game.turn())
                throw new IllegalArgumentException("It is another player's turn");
            if(!input.has("revision") || input.get("revision").getAsLong()!=game.revision())
                throw new IllegalArgumentException("State changed; try again");
            switch(type) {
                case "MOVE_PAWN" -> game.move(new Move(integer(input,"destination")));
                case "PLACE_WALL" -> game.place(integer(input,"wallId"));
                case "BOT_MOVE" -> {
                    if(!room.mode.equals("local")) throw new IllegalArgumentException("Bot testing is local only");
                    int target=HexBoard.INSTANCE.target(game.turn());
                    int move=MoveGenerator.pawnMoves(game).stream().min(Comparator
                        .comparingInt((Integer c)->PathFinder.distance(c,target,game.blocked()))
                        .thenComparingInt(Integer::intValue)).orElseThrow();
                    game.move(new Move(move));
                }
                default -> throw new IllegalArgumentException("Unknown action");
            }
            room.touched=System.currentTimeMillis(); broadcast(room);
        } catch(RuntimeException error) {
            send(socket,Map.of("type","ERROR","message",error instanceof IllegalArgumentException
                ? Objects.toString(error.getMessage(),"Invalid action") : "Invalid action payload"));
        }
    }
    private void join(WebSocket socket,JsonObject input) {
        if(members.containsKey(socket)) throw new IllegalArgumentException("Already joined");
        String id=string(input,"room"),mode=string(input,"mode");
        if(!id.matches("[a-zA-Z0-9_-]{1,32}") || !Set.of("local","online").contains(mode))
            throw new IllegalArgumentException("Invalid room or mode");
        long now=System.currentTimeMillis();
        rooms.values().removeIf(r->r.connections.isEmpty() && now-r.touched>EXPIRY);
        Room room=rooms.get(id);
        if(room==null) {
            if(rooms.size()>=256) throw new IllegalArgumentException("Room capacity reached; try later");
            room=new Room(id,mode); rooms.put(id,room);
        }
        if(!room.mode.equals(mode)) throw new IllegalArgumentException("This room uses a different mode");
        if(mode.equals("local") && !room.connections.isEmpty())
            throw new IllegalArgumentException("Local room already in use");
        int seat=-1; String token="";
        if(mode.equals("online")) {
            String supplied=input.has("token")?input.get("token").getAsString():"";
            for(int p=0;p<3;p++) if(supplied.equals(room.tokens[p])) seat=p;
            if(seat>=0 && room.seats[seat]!=null) throw new IllegalArgumentException("Seat already connected");
            if(seat<0) {
                for(int p=0;p<3;p++) if(room.tokens[p]==null) { seat=p; break; }
                if(seat<0) throw new IllegalArgumentException("Room full; reconnect with your saved seat");
                room.tokens[seat]=UUID.randomUUID().toString();
            }
            token=room.tokens[seat]; room.seats[seat]=socket;
        }
        room.connections.add(socket); room.touched=now; members.put(socket,new Member(room,seat));
        send(socket,Map.of("type","WELCOME","room",id,"seat",seat,"token",token,
            "cells",HexBoard.INSTANCE.cells(),"wallCatalog",HexBoard.INSTANCE.walls(),
            "targets",List.of(HexBoard.INSTANCE.target(0),HexBoard.INSTANCE.target(1),HexBoard.INSTANCE.target(2))));
        broadcast(room);
    }
    public synchronized void close(WebSocket socket) {
        Member member=members.remove(socket);
        if(member==null) return;
        Room room=member.room(); room.connections.remove(socket);
        if(member.seat()>=0) room.seats[member.seat()]=null;
        room.touched=System.currentTimeMillis(); broadcast(room);
    }
    private void broadcast(Room room) {
        List<Boolean> connected=new ArrayList<>();
        for(WebSocket seat:room.seats) connected.add(seat!=null);
        Map<String,Object> payload=Map.of("type",room.game.winner()>=0?"GAME_OVER":"STATE",
            "state",room.game.snapshot(),"ready",room.ready(),"connected",connected);
        for(WebSocket socket:List.copyOf(room.connections)) send(socket,payload);
    }
    private static void send(WebSocket socket,Object data) {
        if(socket.isOpen()) try { socket.send(JSON.toJson(data)); } catch(RuntimeException ignored) {}
    }
    private static String string(JsonObject input,String key) {
        if(!input.has(key) || !input.get(key).isJsonPrimitive() || !input.get(key).getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("Missing "+key);
        return input.get(key).getAsString();
    }
    private static int integer(JsonObject input,String key) {
        if(!input.has(key) || !input.get(key).isJsonPrimitive() || !input.get(key).getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("Invalid "+key);
        return input.get(key).getAsBigDecimal().intValueExact();
    }
}
