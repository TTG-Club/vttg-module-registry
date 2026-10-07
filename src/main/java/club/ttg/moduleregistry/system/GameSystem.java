package club.ttg.moduleregistry.system;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Игровая система VTTG, под которую можно заявить модуль.
 *
 * {@code id} — тот же, что в {@code system.json} системы и в
 * {@code compatibleSystems} модуля: по нему VTTG фильтрует каталог.
 */
@Entity
@Table(name = "game_systems")
public class GameSystem {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected GameSystem() {
    }

    public GameSystem(String id, String name, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void rename(String name) {
        this.name = name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
