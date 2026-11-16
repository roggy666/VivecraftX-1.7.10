package lol.gzmc.vivecraftx.render;

import net.minecraft.client.resources.I18n;

/**
 * The characters on the in-world keyboard, as a named set of two thirteen-column grids.
 *
 * <p>Upstream keeps only the two grids, as plain strings in the settings file, and leaves the
 * player to edit them by hand. That is what makes a non-English board possible at all -- a key
 * carries a character, not a key code -- but nobody is going to type out fifty-two characters in
 * a text editor to get a Cyrillic keyboard, so the named sets below are there to be picked from
 * instead. The strings stay the authority: a grid that matches none of these is the player's own
 * and is reported as such rather than quietly replaced.
 *
 * <p>Thirteen columns because that is the width the grids are sliced at, and the slicing is what
 * decides the shape of the board. Every grid here is four full rows of thirteen, so switching
 * between them does not move a single key.
 */
public final class VRKeyboardLayout {

    /** Where the grids are sliced. Shared with the board that draws them. */
    public static final int COLUMNS = 13;

    /**
     * US QWERTY, character for character as upstream ships it.
     *
     * <p>The two columns on the right of the third and fourth rows carry both forms of a mark, so
     * shift leaving them alone is deliberate rather than an omission.
     */
    private static final VRKeyboardLayout EN = new VRKeyboardLayout("en",
            "`1234567890-="
            + "qwertyuiop[]\\"
            + "asdfghjkl;':\""
            + "zxcvbnm,./?<>",
            "~!@#$%^&*()_+"
            + "QWERTYUIOP{}|"
            + "ASDFGHJKL;':\""
            + "ZXCVBNM,./?<>");

    /**
     * Russian ЙЦУКЕН, on the same grid.
     *
     * <p>Twelve letters and a backslash on the second row, eleven and two marks on the third,
     * nine and four on the fourth: that is where the thirty-three letters of the alphabet land
     * when the digit row is kept where an English typist expects it. The shifted digit row is the
     * real one -- Shift+3 is № on a Russian keyboard, not #.
     */
    private static final VRKeyboardLayout RU = new VRKeyboardLayout("ru",
            "ё1234567890-="
            + "йцукенгшщзхъ\\"
            + "фывапролджэ,."
            + "ячсмитьбю/?<>",
            "Ё!\"№;%:?*()_+"
            + "ЙЦУКЕНГШЩЗХЪ|"
            + "ФЫВАПРОЛДЖЭ<>"
            + "ЯЧСМИТЬБЮ/?<>");

    private static final VRKeyboardLayout[] BUILT_IN = { EN, RU };

    /**
     * The player's own grid, if they have had one and switched off it.
     *
     * <p>Switching layouts overwrites the two settings strings, so a hand-written grid would be
     * gone the moment somebody pressed the layout key to look at the others. Remembering it here
     * puts it back in the ring, which means a full turn round the ring returns to it. It is not
     * saved, because by then it is no longer what the settings file says.
     */
    private static String[] remembered;

    public final String id;
    public final String keys;
    public final String shifted;

    private VRKeyboardLayout(String id, String keys, String shifted) {
        this.id = id;
        this.keys = keys;
        this.shifted = shifted;
    }

    /** The two grids, in the order the settings fields hold them. */
    public String[] grids() {
        return new String[] { this.keys, this.shifted };
    }

    /**
     * Which built-in set this pair of grids is, or null for one the player wrote themselves.
     */
    public static VRKeyboardLayout matching(String keys, String shifted) {
        for (VRKeyboardLayout layout : BUILT_IN) {
            if (layout.keys.equals(keys) && layout.shifted.equals(shifted)) {
                return layout;
            }
        }
        return null;
    }

    /**
     * What to call this pair of grids, on the key that switches them and in the settings.
     */
    public static String label(String keys, String shifted) {
        VRKeyboardLayout layout = matching(keys, shifted);
        return I18n.format("vivecraftx.keyboard.layout." + (layout == null ? "custom" : layout.id));
    }

    /**
     * The next pair of grids round the ring, which includes the player's own if there is one.
     */
    public static String[] next(String keys, String shifted) {
        VRKeyboardLayout current = matching(keys, shifted);
        if (current == null) {
            // Leaving a hand-written grid: hold on to it so the ring comes back round to it.
            remembered = new String[] { keys, shifted };
            return BUILT_IN[0].grids();
        }
        for (int i = 0; i < BUILT_IN.length; i++) {
            if (BUILT_IN[i] == current) {
                if (i + 1 < BUILT_IN.length) {
                    return BUILT_IN[i + 1].grids();
                }
                return remembered != null
                        ? new String[] { remembered[0], remembered[1] }
                        : BUILT_IN[0].grids();
            }
        }
        return BUILT_IN[0].grids();
    }

    /**
     * Which set to start a player off with, from the language the game is already in.
     *
     * <p>{@code gameSettings.language} rather than Forge's language registry: the registry says
     * which translations a mod has supplied, not which one the player is reading, and in this
     * version it has no notion of the current locale at all. The field is a plain locale code,
     * is set before anything here is built, and is the same thing the resource loader uses.
     *
     * <p>Only ever consulted when the settings file has nothing to say, so a saved grid -- named
     * or hand-written -- is never overridden by the game's language changing.
     */
    public static VRKeyboardLayout forLanguage(String language) {
        if (language != null) {
            int mark = language.indexOf('_');
            String code = (mark < 0 ? language : language.substring(0, mark)).toLowerCase();
            for (VRKeyboardLayout layout : BUILT_IN) {
                if (layout.id.equals(code)) {
                    return layout;
                }
            }
        }
        return EN;
    }
}
