import { describe, it, expect } from "vitest";
import {
  displayInfoForName,
  cardImagePath,
  cardArtAttrs,
  resolveIdentity,
  roman,
  romanToInt,
  UNKNOWN_CARD,
  CARD_MISSING_ART,
  DISPLAY_CATALOG,
} from "./cardDisplay.ts";

describe("roman numerals (display only)", () => {
  it("formats the classic range", () => {
    expect(roman(1)).toBe("I");
    expect(roman(2)).toBe("II");
    expect(roman(3)).toBe("III");
    expect(roman(4)).toBe("IV");
    expect(roman(5)).toBe("V");
    expect(roman(9)).toBe("IX");
    expect(roman(10)).toBe("X");
    expect(roman(1987)).toBe("MCMLXXXVII");
  });

  it("falls back to the integer beyond the classic range so high levels never break", () => {
    expect(roman(4000)).toBe("4000");
    expect(roman(0)).toBe("0");
  });

  it("round-trips through romanToInt", () => {
    expect(romanToInt("IV")).toBe(4);
    expect(romanToInt("XII")).toBe(12);
    expect(romanToInt("ZZ")).toBeNull();
  });
});

describe("displayInfoForName — stable id + numeric level", () => {
  it("renders the base card at level 1", () => {
    expect(displayInfoForName("FARM", 1).name).toBe("Farm");
  });

  it("appends a roman-numeral level suffix for upgrades (the original bug: level IV)", () => {
    expect(displayInfoForName("FARM", 4).name).toBe("Farm IV"); // was 'Unknown Card' pre-fix
  });

  it("keeps a very high level recognised — never Unknown Card (level X)", () => {
    const info = displayInfoForName("FARM", 10);
    expect(info.name).toBe("Farm X");
    expect(info).not.toBe(UNKNOWN_CARD);
  });

  it("defaults an absent level to 1 (backward-compatible wire payloads)", () => {
    expect(displayInfoForName("FARM").name).toBe("Farm");
  });

  it("still resolves legacy level-in-name payloads (FARM II / FARM III)", () => {
    expect(displayInfoForName("FARM II", 1).name).toBe("Farm II");
    expect(displayInfoForName("FARM III", 1).name).toBe("Farm III");
  });

  it("recovers from the pre-fix buggy name 'FARM III II' instead of showing Unknown Card", () => {
    const info = displayInfoForName("FARM III II", 1);
    expect(info).not.toBe(UNKNOWN_CARD);
    expect(info.description).toBe(DISPLAY_CATALOG.FARM.description);
  });

  it("returns Unknown Card only for a genuinely unknown identity", () => {
    expect(displayInfoForName("NOT_A_REAL_CARD", 1)).toBe(UNKNOWN_CARD);
  });

  it("resolves the combat cards that used to be missing from the client catalog", () => {
    for (const id of ["RAIDERS_STRIKE", "WARBAND", "SIEGE", "ONSLAUGHT", "ARMORY"]) {
      expect(displayInfoForName(id, 1)).not.toBe(UNKNOWN_CARD);
    }
  });
});

describe("resolveIdentity", () => {
  it("splits a stable id + level", () => {
    expect(resolveIdentity("FARM", 4)).toEqual({ base: "FARM", level: 4 });
  });
  it("recovers base + level from a legacy suffixed name", () => {
    expect(resolveIdentity("MINE III", 1)).toEqual({ base: "MINE", level: 3 });
  });
  it("returns null for an unknown base", () => {
    expect(resolveIdentity("WIDGETS", 1)).toBeNull();
  });
});

describe("cardImagePath — artwork keyed off the base identity", () => {
  it("uses the base card's PNG for any level (Farm IV → farm.png)", () => {
    expect(cardImagePath("FARM")).toBe("/img/farm.png");
    expect(cardImagePath("FARM II")).toBe("/img/farm.png"); // legacy name still base art
  });

  it("honours the file-name overrides", () => {
    expect(cardImagePath("SCHOOL_HOUSE")).toBe("/img/schoolhouse.png");
    expect(cardImagePath("MASON_GUILD")).toBe("/img/masons_guild.png");
    expect(cardImagePath("FORTRESS_WALL")).toBe("/img/fortress.png");
  });
});

describe("cardArtAttrs — missing art falls back to a placeholder, not Unknown Card", () => {
  it("emits an onerror that swaps in the placeholder", () => {
    const attrs = cardArtAttrs("ARMORY"); // ARMORY has no PNG yet
    expect(attrs).toContain('src="/img/armory.png"');
    expect(attrs).toContain(CARD_MISSING_ART);
    expect(attrs).toContain("onerror");
  });
});
