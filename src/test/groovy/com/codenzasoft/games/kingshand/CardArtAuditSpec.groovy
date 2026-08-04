package com.codenzasoft.games.kingshand

import spock.lang.Specification
import java.lang.reflect.Modifier
import java.util.regex.Pattern

/**
 * Texture / card-art audit. Cross-checks the server card registry against the on-disk PNGs and the
 * client display catalog so a card can never silently resolve to {@code UNKNOWN CARD} or a broken
 * image.
 *
 * <p>Artwork is keyed off a card's <em>stable base identity</em> ({@link Card#name()}, lowercased,
 * with a small override map that mirrors {@code ui/src/kh/cardDisplay.ts}'s IMAGE_NAME_OVERRIDES).
 * Upgraded cards ("Farm IV") reuse the base art, so no per-level PNGs are needed.
 */
class CardArtAuditSpec extends Specification {

    /** Mirror of IMAGE_NAME_OVERRIDES in ui/src/kh/cardDisplay.ts. */
    static final Map<String, String> OVERRIDES = [
            SCHOOL_HOUSE  : "schoolhouse",
            MASON_GUILD   : "masons_guild",
            FORTRESS_WALL : "fortress",
            SABOTEURS     : "guard_post",
            CROP_RAID     : "the_hunt",
            WINTER_RAIDERS: "desperate_times",
    ]

    /**
     * Cards with no dedicated PNG yet: they render with the intentional missing-art placeholder
     * (ui/public/img/card-missing.svg) and the correct name/description — never UNKNOWN CARD.
     */
    static final Set<String> APPROVED_MISSING_ART = [
            "ARMORY", "RAIDERS_STRIKE", "WARBAND", "SIEGE", "ONSLAUGHT",
    ] as Set

    static String textureKey(String name) {
        OVERRIDES.getOrDefault(name, name.toLowerCase())
    }

    static List<String> allServerCardNames() {
        CardCatalog.getDeclaredFields()
                .findAll { Modifier.isStatic(it.modifiers) && it.type == Card }
                .collect { it.setAccessible(true); ((Card) it.get(null)).name() }
                .unique()
    }

    static File resolve(String relative) {
        for (String prefix : ["", "game-server/"]) {
            def f = new File(prefix + relative)
            if (f.exists()) return f
        }
        return new File(relative)
    }

    File imgDir = resolve("ui/public/img")
    File displayCatalogTs = resolve("ui/src/kh/cardDisplay.ts")

    Set<String> existingPngs() {
        (imgDir.listFiles() ?: [] as File[])
                .findAll { it.name.endsWith(".png") && !it.name.startsWith("bg-") }
                .collect { it.name } as Set
    }

    Set<String> clientDisplayKeys() {
        def keys = [] as Set
        def m = Pattern.compile(/(?m)^ {2}([A-Z0-9_]+):\s*\{/).matcher(displayCatalogTs.text)
        while (m.find()) keys << m.group(1)
        return keys
    }

    /** Names whose texture PNG is missing and which are not on the approved-placeholder list. */
    List<String> missingArt(Collection<String> names, Set<String> pngs) {
        names.findAll { !pngs.contains(textureKey(it) + ".png") && !APPROVED_MISSING_ART.contains(it) }
    }

    def "test fixtures are present"() {
        expect:
        imgDir.isDirectory()
        displayCatalogTs.isFile()
    }

    def "every registered card resolves to an existing PNG or an explicitly approved placeholder"() {
        when:
        def missing = missingArt(allServerCardNames(), existingPngs())

        then:
        missing.isEmpty()
    }

    def "the audit detects a deliberately missing texture asset"() {
        expect:
        missingArt(["DELIBERATELY_MISSING_TEST_ASSET"], existingPngs()) == ["DELIBERATELY_MISSING_TEST_ASSET"]
    }

    def "every server card ID is present in the client display catalog (frontend/backend parity)"() {
        given:
        def clientKeys = clientDisplayKeys()

        when:
        def unmapped = allServerCardNames().findAll { !clientKeys.contains(it) }

        then:
        unmapped.isEmpty()
    }

    def "every shared-art override points at a real PNG"() {
        given:
        def pngs = existingPngs()

        expect:
        OVERRIDES.values().every { pngs.contains(it + ".png") }
    }

    def "no non-background PNG is left unused by the card registry"() {
        given:
        def used = allServerCardNames().collect { textureKey(it) + ".png" } as Set

        when:
        def unused = existingPngs().findAll { !used.contains(it) }

        then:
        unused.isEmpty()
    }
}
