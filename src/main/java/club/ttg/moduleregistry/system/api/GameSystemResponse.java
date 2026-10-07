package club.ttg.moduleregistry.system.api;

import club.ttg.moduleregistry.system.GameSystem;

public record GameSystemResponse(String id, String name) {

    public static GameSystemResponse from(GameSystem system) {
        return new GameSystemResponse(system.getId(), system.getName());
    }
}
