// ─── Player Handbook ─────────────────────────────────────────────────────────
//
// Structured reference for all core King's Hand mechanics.  Each chapter groups
// related sections; each section has a title and an HTML body string.
//
// This file is the single source of truth for handbook content — update it here
// and all scenes that render the handbook will reflect the change automatically.

export interface HandbookSection {
  title: string;
  /** Inner HTML rendered inside the section body. Keep to simple inline tags. */
  body: string;
}

export interface HandbookChapter {
  icon: string;
  title: string;
  sections: HandbookSection[];
}

export const HANDBOOK: HandbookChapter[] = [
  // ── Phase Cycle ────────────────────────────────────────────────────────────
  {
    icon: "🔄",
    title: "Phase Cycle",
    sections: [
      {
        title: "How a season works",
        body: `Each season consists of three phases played in order:
               <br><br>
               <b>I. Add</b> — You are offered three cards to draft into your deck.
               You may reroll the offer (at a production cost) or skip entirely.
               <br><br>
               <b>II. Random Event</b> — A seasonal event fires and you choose one
               of its options.  If no eligible event exists for this season and year,
               the phase is skipped automatically.
               <br><br>
               <b>III. Play</b> — You draw three cards from your deck and may play
               any of them onto your table.  When you are ready, click
               <em>End and Run</em> to resolve the table and advance to the next season.`,
      },
      {
        title: "Season order",
        body: `Seasons cycle in order: <b>Spring → Summer → Fall → Winter</b>, then
               back to Spring.  Completing all four seasons advances the year by one.
               Each new year increases your deck size and the severity of winter events.`,
      },
    ],
  },

  // ── Resources ──────────────────────────────────────────────────────────────
  {
    icon: "📊",
    title: "Resources",
    sections: [
      {
        title: "Population 👥",
        body: `Population is your most important resource — if it drops below 1, your
               kingdom falls and the game ends.
               <br><br>
               Population grows naturally over time through the food surplus mechanic
               and certain cards and events.  It can be reduced by starvation and
               negative events.`,
      },
      {
        title: "Production ⚒️",
        body: `Production is your currency for action.  Playing a card costs its listed
               production value.  Demolishing a card costs 20% of that card's production
               requirement.  Rerolling the Add phase offer also costs production
               (increasing by 10 each reroll within the same season).
               <br><br>
               Production resets each season based on what your table cards generate.`,
      },
      {
        title: "Food 🌾",
        body: `Food is consumed at the end of every season when you End and Run.
               Surplus food carries over to the next season — it is never wasted.
               A persistent food stockpile is your safety net against bad seasons.`,
      },
    ],
  },

  // ── Food & Population ──────────────────────────────────────────────────────
  {
    icon: "🌾",
    title: "Food, Starvation & Surplus",
    sections: [
      {
        title: "Food consumption",
        body: `At the end of each season (when you End and Run), every unit of
               population consumes food:
               <br><br>
               &nbsp;&nbsp;• <b>Standard seasons</b> (Spring, Summer, Fall): <b>1 food</b> per population.
               <br>
               &nbsp;&nbsp;• <b>Winter</b>: <b>2 food</b> per population.
               <br><br>
               Only the integer (whole) part of your population is counted for
               consumption — fractional population does not eat.`,
      },
      {
        title: "Starvation",
        body: `If your food is insufficient to feed everyone, a food deficit occurs.
               Any shortfall triggers starvation:
               <br><br>
               <b>Population lost = 0.5 × food deficit</b>
               <br><br>
               Example: 10 population needs 10 food, but you only have 6.
               The deficit is 4, so you lose 2 population.
               <br><br>
               Starvation can also be triggered by events that reduce food below zero.
               If population drops below 1 for any reason, the game ends immediately.`,
      },
      {
        title: "Food surplus growth",
        body: `If food remains after everyone has been fed, your population grows
               proportionally to the surplus:
               <br><br>
               <b>Growth rate = surplus ÷ population (capped at 50%)</b>
               <br><br>
               Example: 10 population, 12 food consumed → 2 food left.
               Growth rate = 2 ÷ 10 = 20%.  Population becomes 12.
               The 2 surplus food is <em>not</em> consumed — it stays in your stockpile.
               <br><br>
               This mechanic rewards keeping a food surplus; a well-fed kingdom
               naturally grows without needing population cards.`,
      },
    ],
  },

  // ── Cards ──────────────────────────────────────────────────────────────────
  {
    icon: "🃏",
    title: "Cards",
    sections: [
      {
        title: "Card frequency: Recurring vs Single-use",
        body: `Every card has a frequency type shown on its badge:
               <br><br>
               <b>Recurring</b> — The card fires every season it sits on your table.
               These are your engine cards; build around them for compounding value.
               <br><br>
               <b>Single-use</b> — The card fires once when you End and Run, then
               is permanently removed from your deck.  Use them for burst value
               (a big one-time harvest, a stockpile of firewood) when you need it most.`,
      },
      {
        title: "Playing a card",
        body: `To play a card from your hand, you must meet <em>both</em> requirements:
               <br><br>
               &nbsp;&nbsp;• <b>Production cost</b> — your current production must be at
               least the card's listed cost.  Production is deducted on play.
               <br>
               &nbsp;&nbsp;• <b>Population requirement</b> — your population must be at
               least the listed value.  Population is <em>not</em> deducted.
               <br><br>
               Unplayed hand cards are discarded to your discard pile when you End and Run.`,
      },
      {
        title: "Upgrading a card",
        body: `If a card in your hand matches a card already on your table (same type),
               you can <em>upgrade</em> it instead of filling a new slot.  An upgrade
               amplifies the existing table card's effect — effectively doubling its
               output — without using an additional slot.
               <br><br>
               Upgraded cards are highlighted with a gold border and an "UPGRADES TABLE" tag.`,
      },
      {
        title: "The table",
        body: `Your table holds up to <b>5 cards</b>.  When you End and Run, cards fire
               from left to right.  Recurring cards remain on the table after firing;
               single-use cards are removed.
               <br><br>
               You can demolish any table card at any time during the Play phase.
               Demolishing costs <b>20% of the card's production requirement</b>
               (rounded up) and permanently removes it from your deck.`,
      },
    ],
  },

  // ── Deck, Draw & Discard ───────────────────────────────────────────────────
  {
    icon: "📖",
    title: "Deck, Draw & Discard",
    sections: [
      {
        title: "Your deck",
        body: `Your deck is the full collection of cards you have built across all
               seasons.  Cards are organised by season — a Farm you drafted in Spring
               lives in your Spring deck and can only be drawn during Spring.
               <br><br>
               The <b>Deck</b> button (top-right) shows every card you own, grouped by
               season and sorted into draw, hand, and discard sub-piles.`,
      },
      {
        title: "Draw pile",
        body: `At the start of each Play phase, up to <b>3 cards</b> are drawn from the
               current season's draw pile into your hand.  If the draw pile runs out,
               the discard pile is shuffled back in automatically.
               <br><br>
               The <b>Draw</b> button (bottom-left) shows the cards remaining in the
               current season's draw pile.`,
      },
      {
        title: "Discard pile",
        body: `Played cards and unplayed hand cards are moved to the discard pile when
               you End and Run.  Recurring table cards stay on the table and are
               <em>not</em> discarded.  Single-use cards that fire are removed from the
               game entirely — they do not return to the discard pile.
               <br><br>
               The <b>Discard</b> button (bottom-right) shows the current season's
               discard pile.`,
      },
    ],
  },

  // ── Events ────────────────────────────────────────────────────────────────
  {
    icon: "⚡",
    title: "Random Events",
    sections: [
      {
        title: "How events work",
        body: `Each season, one random event is rolled from that season's event pool.
               Events have a minimum year requirement — harsher events only appear as
               your run extends.  You are presented with two or more options and must
               pick one; there is no way to skip an event once it fires.
               <br><br>
               Event effects are applied immediately before the Play phase begins.`,
      },
      {
        title: "Winter scaling ❄️",
        body: `Winter events grow more severe over time.  Every effect modifier in a
               winter event is <b>multiplied by the current year</b> before it is applied.
               <br><br>
               Example: a −5 production effect in Year 3 Winter becomes −15.
               <br><br>
               The winter banner displayed during the Random Event phase shows the
               active scaling multiplier.  Plan ahead — winters only get harsher.`,
      },
      {
        title: "Event floors",
        body: `Event effects that reduce resources can never push <b>production</b> or
               <b>food</b> below zero.  If a food reduction would go negative, the
               surplus deficit instead triggers starvation (see the Food section).`,
      },
    ],
  },

  // ── Survival tips ──────────────────────────────────────────────────────────
  {
    icon: "💡",
    title: "Tips",
    sections: [
      {
        title: "Balance food and population growth",
        body: `More population eats more food but also unlocks more powerful cards.
               Keep a food surplus so you grow naturally each season rather than
               spending card slots on population cards.`,
      },
      {
        title: "Plan for winter early",
        body: `Winter consumes twice the food and events scale with the year.  Enter
               each winter with a stockpile of at least <em>2 × population</em> food
               and some production to spare.`,
      },
      {
        title: "Single-use cards for bursts",
        body: `A Harvest or Seeds card played at the right moment can save a kingdom
               on the brink of starvation.  Keep one or two single-use cards in hand
               as an emergency reserve rather than burning them every season.`,
      },
      {
        title: "Upgrade before expanding",
        body: `Upgrading a table card doubles its effect for just one hand card.
               Before filling a new table slot, check whether you already have a
               matching card in hand — the upgrade is often better value than a new slot.`,
      },
    ],
  },
];
