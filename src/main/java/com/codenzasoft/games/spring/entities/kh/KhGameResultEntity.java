package com.codenzasoft.games.spring.entities.kh;

import com.codenzasoft.games.kingshand.Season;
import com.codenzasoft.games.spring.entities.UserEntity;
import jakarta.persistence.*;
import java.util.Date;
import java.util.UUID;

@Entity
@Table(name = "kh_game_results")
public class KhGameResultEntity {

  @Id
  @Column(name = "game_id")
  private UUID gameId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false, updatable = false)
  private UserEntity user;

  @Column(name = "year_survived", nullable = false, updatable = false)
  private int yearSurvived;

  @Enumerated(EnumType.STRING)
  @Column(name = "season_survived", nullable = false, updatable = false)
  private Season seasonSurvived;

  @Column(name = "season_order", nullable = false, updatable = false)
  private int seasonOrder;

  @Column(name = "peak_population", nullable = false, updatable = false)
  private double peakPopulation;

  @Column(name = "created_at", updatable = false, nullable = false)
  private Date createdAt;

  @PrePersist
  public void onCreate() {
    if (createdAt == null) {
      createdAt = new Date();
    }
  }

  public KhGameResultEntity() {}

  public UUID getGameId() {
    return gameId;
  }

  public void setGameId(UUID gameId) {
    this.gameId = gameId;
  }

  public UserEntity getUser() {
    return user;
  }

  public void setUser(UserEntity user) {
    this.user = user;
  }

  public int getYearSurvived() {
    return yearSurvived;
  }

  public void setYearSurvived(int yearSurvived) {
    this.yearSurvived = yearSurvived;
  }

  public Season getSeasonSurvived() {
    return seasonSurvived;
  }

  public void setSeasonSurvived(Season season) {
    this.seasonSurvived = season;
    this.seasonOrder = season.ordinal();
  }

  public int getSeasonOrder() {
    return seasonOrder;
  }

  public double getPeakPopulation() {
    return peakPopulation;
  }

  public void setPeakPopulation(double peakPopulation) {
    this.peakPopulation = peakPopulation;
  }

  public Date getCreatedAt() {
    return createdAt;
  }
}
