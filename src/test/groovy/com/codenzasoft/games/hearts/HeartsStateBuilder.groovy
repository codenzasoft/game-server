package com.codenzasoft.games.hearts

import com.codenzasoft.games.common.cards.Card

class HeartsStateBuilder {

    static final List<String> PLAYERS = ["alice", "bob", "carol", "dave"]

    static PlayedCard played(String player, Card card) {
        new PlayedCard(player, card)
    }

    static TrickState trick(Map opts = [:]) {
        new TrickState(
            opts.get('number', 1) as int,
            opts.get('leader', PLAYERS[0]) as String,
            opts.get('playedCards', []) as List
        )
    }

    // hands: [:] makes RoundState.isComplete() return true (zero cards remain)
    static RoundState round(Map opts = [:]) {
        new RoundState(
            opts.get('number', 1) as int,
            opts.get('pass', Pass.KEEPER) as Pass,
            opts.get('dealer', PLAYERS[0]) as String,
            [:], [:], true,
            opts.get('tricks', []) as List,
            [:]
        )
    }

    static HeartsState state(Map opts = [:]) {
        new HeartsState(
            opts.get('players', PLAYERS) as List,
            opts.get('rounds', []) as List,
            opts.get('over', false) as boolean,
            opts.get('quit', false) as boolean
        )
    }
}
