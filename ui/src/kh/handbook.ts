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
  // ── Quick Start ────────────────────────────────────────────────────────────
  {
    icon: "🚀",
    title: "Quick Start",
    sections: [
      {
        title: "Your goal",
        body: `Keep your population alive. If it hits zero, the game ends.`,
      },
      {
        title: "Each season",
        body: `<b>Add</b> — pick one of three offered cards to add to your deck. Skip or reroll if nothing fits.
               <br><br>
               <b>Random Event</b> — an event fires and you choose a response. Read both options before picking.
               <br><br>
               <b>Play</b> — draw up to 3 cards and place them on your table. Hit Play when done.
               Cards fire left to right, then food is consumed. Repeat each season.`,
      },
      {
        title: "The one rule",
        body: `Make sure you have enough food before you Play. Standard seasons cost 1 food per population; Winter costs 2.`,
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
        body: `If population hits zero, the game ends. Population also determines how many
               workers you have — more population means more cards can be staffed each season.
               <br><br>
               Population grows when you have leftover food after feeding everyone, and drops
               through starvation or negative events.`,
      },
      {
        title: "Population Cap 🏠",
        body: `Population can never grow past your population cap. It only rises when something
               explicitly raises it — most cards never touch it, but two Great Works do: The
               Great Reservoir and The Great Road both raise the cap a little more each season
               their construction advances, not just once when finished.`,
      },
      {
        title: "Production ⚒️",
        body: `Production is spent to play cards, demolish table cards, and reroll the Add
               phase offer. It resets each season based on your table cards.`,
      },
      {
        title: "Food 🌾",
        body: `Food is consumed each season when you Play. Standard seasons cost 1 food per
               population; Winter costs 2. Surplus carries over — it is never wasted.
               <br><br>
               If you don't have enough food, population drops. If population drops below 1,
               the game ends.`,
      },
      {
        title: "Gold 💰",
        body: `Gold is unlocked once the Treasury Great Work is completed. From then on it
               appears in your resource bar and can be earned and spent by cards and events
               just like any other resource.`,
      },
      {
        title: "Strength ⚔️",
        body: `Strength is unlocked once the Barracks Great Work is completed. From then on it
               appears in your resource bar and can be earned and spent by cards and events
               just like any other resource.`,
      },
    ],
  },

  // ── Cards ──────────────────────────────────────────────────────────────────
  {
    icon: "🃏",
    title: "Cards",
    sections: [
      {
        title: "Recurring vs Single-use",
        body: `<b>Recurring</b> cards fire every season they sit on your table and stay there
               after firing. Build your engine around these.
               <br><br>
               <b>Single-use</b> cards fire once and are permanently removed from your deck.
               Use them when you need a burst of resources at the right moment.`,
      },
      {
        title: "Playing a card",
        body: `Playing a card costs its listed production value, deducted immediately. You can
               play any card regardless of population, but understaffed cards fire at reduced
               power. Unplayed hand cards are discarded when you Play.`,
      },
      {
        title: "Upgrading a card",
        body: `If a card in your hand matches one already on your table, you can upgrade it
               instead of filling a new slot. The hand card's modifiers are added to the table
               card's current modifiers — including any engine boosts already applied.
               <br><br>
               Upgradeable cards are highlighted with a gold border and an "UPGRADES TABLE" tag.`,
      },
      {
        title: "The table",
        body: `Your table holds up to 5 cards. Cards fire left to right when you Play.
               Recurring cards stay; single-use cards are removed.
               <br><br>
               You can demolish a table card at any time during the Play phase. Demolishing
               costs production and permanently removes the card from your deck.`,
      },
    ],
  },

  // ── Workers & Staffing ─────────────────────────────────────────────────────
  {
    icon: "👷",
    title: "Workers & Staffing",
    sections: [
      {
        title: "How staffing works",
        body: `When you Play, your population is converted into workers and distributed left
               to right across your table, filling each card's staff requirement in order.
               Cards that don't receive enough workers fire at half power or not at all.
               <br><br>
               Each table card shows a colour-coded border and staffing badge so you can see
               how it will resolve before you commit:
               <br><br>
               &nbsp;&nbsp;• <span style="color:#4caf50"><b>Green</b></span> — fully staffed, fires at full power.
               <br>
               &nbsp;&nbsp;• <span style="color:#f9a825"><b>Yellow</b></span> — half staffed, fires at half power.
               <br>
               &nbsp;&nbsp;• <span style="color:#e53935"><b>Red</b></span> — understaffed, will not fire.`,
      },
      {
        title: "Card order matters",
        body: `Because workers fill left to right, your leftmost cards are always staffed
               first. Drag cards to reorder them — put your most important recurring cards
               on the left so they are never left without workers.`,
      },
    ],
  },

  // ── Great Works ────────────────────────────────────────────────────────────
  {
    icon: "🏛️",
    title: "Great Works",
    sections: [
      {
        title: "What are Great Works?",
        body: `Great Works are six legendary projects offered through the Add phase like any
               other card. Instead of firing immediately, they go "under construction" on your
               table and need multiple seasons of progress to complete.`,
      },
      {
        title: "Building one",
        body: `Each season, a Great Work under construction only advances if it is fully
               staffed and you can afford its listed cost (production, food, gold, and/or
               strength). If either condition isn't met, that season's progress simply stalls —
               nothing is lost, and it tries again next season. A 🏗️ badge shows how many
               seasons remain.`,
      },
      {
        title: "Unlocking Gold and Strength",
        body: `Two Great Works don't count toward winning: completing the <b>Treasury</b>
               permanently unlocks Gold 💰, and completing the <b>Barracks</b> permanently
               unlocks Strength ⚔️. From then on they appear in your resource bar like any other
               resource.`,
      },
      {
        title: "Winning the game 👑",
        body: `The remaining four Great Works — <b>The Great Reservoir</b>, <b>The Great
               Road</b>, <b>The Great Capital</b>, and <b>The Great Conquest</b> — are win
               conditions. Complete all four and your kingdom triumphs, ending the game in
               victory.`,
      },
    ],
  },

  // ── Deck, Draw & Discard ───────────────────────────────────────────────────
  {
    icon: "📖",
    title: "Deck",
    sections: [
      {
        title: "How your deck works",
        body: `Cards are organised by season — a card drafted in Spring can only be drawn
               during Spring. At the start of each Play phase, up to 3 cards are drawn from
               that season's draw pile. If the draw pile runs out, the discard pile is
               shuffled back in.
               <br><br>
               Every game starts with a small starter deck spread across the seasons. The
               Add phase is how you build on top of it.`,
      },
      {
        title: "Discard pile",
        body: `Played and unplayed hand cards go to the discard pile when you Play. Recurring
               table cards stay on the table. Single-use cards that fire are removed from the
               game entirely — they don't return to the discard pile.`,
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
        body: `Each season a random event fires and you choose one of its options. Harsher
               events only appear in later years. Effects are applied before the Play phase.`,
      },
      {
        title: "Winter scaling ❄️",
        body: `Winter event effects scale up each year — early winters are survivable, later
               ones are not. Non-winter events also scale slightly, but much more slowly.`,
      },
    ],
  },

  // ── Tips ──────────────────────────────────────────────────────────────────
  {
    icon: "💡",
    title: "Tips",
    sections: [
      {
        title: "Keep a food surplus",
        body: `Leftover food after feeding everyone grows your population. A consistent
               surplus is the easiest way to grow without spending card slots on it.`,
      },
      {
        title: "Plan for winter",
        body: `Winter costs twice the food and events hit harder each year. Stock up before
               Fall ends.`,
      },
      {
        title: "Save single-use cards",
        body: `Don't burn single-use cards every season. Keep one or two as an emergency
               reserve for when food runs low.`,
      },
      {
        title: "Left-align your best cards",
        body: `Workers fill left to right. Your most important recurring cards belong on the
               left so they're always staffed, even if population drops.`,
      },
    ],
  },
];
