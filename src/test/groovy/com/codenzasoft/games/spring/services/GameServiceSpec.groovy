package com.codenzasoft.games.spring.services

import com.codenzasoft.games.common.GameType
import com.codenzasoft.games.kingshand.GameEngine
import com.codenzasoft.games.spring.entities.UserEntity
import com.codenzasoft.games.spring.entities.kh.KhGameEntity
import com.codenzasoft.games.spring.repositories.GamePlayersRepository
import com.codenzasoft.games.spring.repositories.GamesRepository
import com.codenzasoft.games.spring.repositories.UsersRepository
import com.codenzasoft.games.spring.services.xo.XoBroadcastService
import com.codenzasoft.games.spring.services.xo.XoGamePersistenceService
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import spock.lang.Specification

class GameServiceSpec extends Specification {

    def gamesRepository = Mock(GamesRepository)
    def usersRepository = Mock(UsersRepository)
    def gamePlayersRepository = Mock(GamePlayersRepository)
    def xoGamePersistenceService = Mock(XoGamePersistenceService)
    def xoBroadcastService = Mock(XoBroadcastService)
    def gameNotificationExecutor = Mock(ThreadPoolTaskExecutor)
    def gameCache = new GameCache()
    def gameService = new GameService()

    def setup() {
        gameService.gamesRepository = gamesRepository
        gameService.usersRepository = usersRepository
        gameService.gamePlayersRepository = gamePlayersRepository
        gameService.xoGamePersistenceService = xoGamePersistenceService
        gameService.xoBroadcastService = xoBroadcastService
        gameService.gameNotificationExecutor = gameNotificationExecutor
        gameService.gameCache = gameCache
    }

    private static UserEntity userWith(UUID id, String name) {
        def user = new UserEntity()
        user.@id = id
        user.userName = name
        user
    }

    def "createKhGame persists a new KH game with the given user as player"() {
        given:
        def userId = UUID.randomUUID()
        def user = userWith(userId, "player1")

        when:
        def entity = gameService.createKhGame(user)

        then:
        1 * gamesRepository.save(_ as KhGameEntity)
        entity instanceof KhGameEntity
        entity.id != null
        entity.gameType == GameType.KH
        entity.players == [user]
    }

    def "getOrBuildKhGame fetches from DB on first call and adds game to cache"() {
        given:
        def gameId = UUID.randomUUID()
        def userId = UUID.randomUUID()
        def user = userWith(userId, "player1")
        def entity = new KhGameEntity(gameId)
        entity.setPlayers([user])
        gamesRepository.findById(gameId) >> Optional.of(entity)

        when:
        def result = gameService.getOrBuildKhGame(gameId)

        then:
        result.isPresent()
        result.get() instanceof GameEngine
        result.get().id == gameId
        result.get().players.size() == 1
        result.get().players[0].id() == userId
        result.get().players[0].name() == "player1"
    }

    def "getOrBuildKhGame adds the built game to the cache"() {
        given:
        def gameId = UUID.randomUUID()
        def user = userWith(UUID.randomUUID(), "player1")
        def entity = new KhGameEntity(gameId)
        entity.setPlayers([user])
        gamesRepository.findById(gameId) >> Optional.of(entity)

        when:
        gameService.getOrBuildKhGame(gameId)

        then:
        gameCache.getGameOfType(gameId, GameType.KH).isPresent()
    }

    def "getOrBuildKhGame returns the cached game on subsequent calls without hitting the DB"() {
        given:
        def gameId = UUID.randomUUID()
        def user = userWith(UUID.randomUUID(), "player1")
        def entity = new KhGameEntity(gameId)
        entity.setPlayers([user])

        when:
        def first = gameService.getOrBuildKhGame(gameId)
        def second = gameService.getOrBuildKhGame(gameId)

        then:
        1 * gamesRepository.findById(gameId) >> Optional.of(entity)
        first.get().is(second.get())
    }
}
