package com.codenzasoft.games.kingshand

import spock.lang.Specification

class DeckSpec extends Specification {

    // Two cards with the SAME name but different effects are NOT equals() — used to prove the
    // name-matching vs record-equality distinction.
    private static Card card(String name, int prod = 0) {
        new Card(name, 0, 0, FrequencyType.RECURRING, [ResourceTarget.PRODUCTION.integer(prod)])
    }

    def "empty() and of(...) build the expected decks"() {
        expect:
        Deck.empty().isEmpty()
        Deck.empty().size() == 0
        Deck.of(card("A"), card("B")).size() == 2
        Deck.of([card("A")]).size() == 1
    }

    def "records give value equality"() {
        expect:
        Deck.of(card("A"), card("B")) == Deck.of(card("A"), card("B"))
        Deck.of(card("A"), card("B")) != Deck.of(card("B"), card("A"))
    }

    def "backing list is a defensive, unmodifiable copy"() {
        given:
        def source = [card("A")] as ArrayList
        def deck = Deck.of(source)

        when: "the caller mutates the source list after construction"
        source.add(card("B"))

        then: "the deck is unaffected"
        deck.size() == 1

        when: "the caller tries to mutate the exposed list"
        deck.asList().add(card("C"))

        then:
        thrown(UnsupportedOperationException)
    }

    def "add / addAll return a new deck and leave the original untouched"() {
        given:
        def deck = Deck.of(card("A"))

        expect:
        deck.add(card("B")).asList()*.name() == ["A", "B"]
        deck.addAll([card("B"), card("C")]).asList()*.name() == ["A", "B", "C"]
        deck.size() == 1   // original unchanged
    }

    def "remove drops the first equal occurrence and is a no-op when absent"() {
        given:
        def a = card("A")
        def deck = Deck.of(a, card("B"), a)

        expect:
        deck.remove(a).asList()*.name() == ["B", "A"]   // only the first A
        deck.remove(card("Z")).size() == 3              // absent -> unchanged contents
    }

    def "removeAt removes by index and rejects out-of-range"() {
        given:
        def deck = Deck.of(card("A"), card("B"))

        expect:
        deck.removeAt(0).asList()*.name() == ["B"]

        when:
        deck.removeAt(5)

        then:
        thrown(IndexOutOfBoundsException)
    }

    def "replace swaps the first occurrence, or throws when absent"() {
        given:
        def a = card("A")
        def deck = Deck.of(a, card("B"))

        expect:
        deck.replace(a, card("A2")).asList()*.name() == ["A2", "B"]

        when:
        deck.replace(card("Z"), card("Y"))

        then:
        thrown(IllegalArgumentException)
    }

    def "contains/indexOf match by record equality; containsName/findByName match by name"() {
        given:
        def strong = card("FARM", 100)
        def weak = card("FARM", 50)   // same name, different effects -> not equals
        def deck = Deck.of(strong)

        expect:
        deck.contains(strong)
        !deck.contains(weak)          // equality distinguishes them
        deck.indexOf(strong) == 0
        deck.indexOf(weak) == -1

        and:
        deck.containsName("FARM")     // name matches either
        deck.findByName("FARM").get() == strong
        deck.findByName("MINE").isEmpty()
    }

    def "subListAfter returns the tail after a card, else empty"() {
        given:
        def a = card("A")
        def b = card("B")
        def c = card("C")
        def deck = Deck.of(a, b, c)

        expect:
        deck.subListAfter(a)*.name() == ["B", "C"]
        deck.subListAfter(c) == []              // last card -> empty
        deck.subListAfter(card("Z")) == []      // absent -> empty
    }

    def "shuffle is deterministic for a seed and preserves the multiset"() {
        given:
        def deck = Deck.of(card("A"), card("B"), card("C"), card("D"))

        when:
        def first = deck.shuffle(new Random(42))
        def second = deck.shuffle(new Random(42))

        then:
        first == second                                   // same seed -> same order
        first.asList() as Set == deck.asList() as Set     // permutation, no cards lost/added
        first.size() == 4
    }

    def "drawTop splits the top #count cards"() {
        given:
        def deck = Deck.of(card("A"), card("B"), card("C"))

        when:
        def result = deck.drawTop(count)

        then:
        result.drawn()*.name() == drawnNames
        result.remaining().asList()*.name() == remainingNames

        where:
        count || drawnNames          | remainingNames
        0     || []                  | ["A", "B", "C"]
        2     || ["A", "B"]          | ["C"]
        3     || ["A", "B", "C"]     | []
        5     || ["A", "B", "C"]     | []   // clamped
    }

    def "drawTop rejects a negative count"() {
        when:
        Deck.of(card("A")).drawTop(-1)

        then:
        thrown(IllegalArgumentException)
    }
}
