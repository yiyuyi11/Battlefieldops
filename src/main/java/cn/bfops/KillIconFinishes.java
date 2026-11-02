package cn.bfops;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * The {@code valorant} mode's cosmetic weapon finishes.
 *
 * <p>This was the one genuinely useful piece of the previous kill-icon layer, kept when the rest of it was
 * deleted. The {@code valorant} mode is not an icon set: it is a set of cosmetic <em>finishes</em> (Gaia,
 * Glitchpop, RGX 11z Pro, …), each shipping its own bar, emblem, frame and ring. Treating it as "one style"
 * would use one of its textures and silently discard the rest, so the finish is a second axis beside the
 * preset: the preset picks the <em>mode</em>, the finish picks the <em>skin</em> within it.
 *
 * <p><b>Derived, not listed.</b> A finish is any prefix that owns at least one part file
 * ({@code <finish>_bar}, {@code _emblem}, {@code _frame}, {@code _ring}, {@code _blade}). Reading that out of
 * {@link IconCatalog} means every finish the pack ships becomes selectable without a hand-kept list that would
 * fall behind on the next import — which is exactly how an earlier draft ended up with a hard-coded eight that
 * missed the {@code _v1}/{@code _v2}/{@code _v3} colourways entirely.
 *
 * <p><b>Why the part list must precede the finish list.</b> Java initialises static fields in source order, and
 * {@link #FINISHES} is computed by a method that reads {@link #PARTS}. Declared the other way round, the first
 * read of {@code PARTS} is {@code null} and the class fails to load with a {@code NullPointerException} inside a
 * list iterator — a failure that reports itself as being in the JDK rather than here.
 */
final class KillIconFinishes {
    /** The part suffixes that identify a finish prefix. Declared before {@link #FINISHES}; see the class note. */
    private static final List<String> PARTS = List.of("emblem", "bar", "frame", "ring", "blade");

    /** Every finish the pack ships, the plain default first. */
    static final List<String> FINISHES = finishes();

    private KillIconFinishes() { }

    static boolean isValid(String finish) {
        return finish != null && FINISHES.contains(finish);
    }

    /** The configured finish, or the first one when unset or unknown. */
    static String orDefault(String finish) {
        return isValid(finish) ? finish : FINISHES.get(0);
    }

    /** The next finish in the cycle, wrapping. */
    static String next(String finish) {
        String current = orDefault(finish);
        return FINISHES.get((FINISHES.indexOf(current) + 1) % FINISHES.size());
    }

    /**
     * A display name for a finish: {@code gaia_v1} becomes {@code Gaia V1}.
     *
     * <p>The pack's names are file stems, and showing one raw in the action bar reads like a path. Title-casing
     * the underscore-separated parts is a small courtesy that makes the feedback legible without a translation
     * table for 27 finishes.
     */
    static String displayName(String finish) {
        String current = orDefault(finish);
        if (current.equals("base")) return "默认";
        StringBuilder out = new StringBuilder();
        for (String part : current.split("_")) {
            if (part.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

    private static List<String> finishes() {
        TreeSet<String> found = new TreeSet<>();
        for (String name : IconCatalog.FAMILY.getOrDefault("valorant", List.of())) {
            // Family-level files (particles, the shared bar and the single headshot emblem) are not a finish;
            // they are the fallback vocabulary and are reached through the part lookup instead.
            if (name.startsWith("particle_") || !name.contains("_")) continue;
            for (String part : PARTS) {
                if (name.endsWith("_" + part)) {
                    found.add(name.substring(0, name.length() - part.length() - 1));
                    break;
                }
            }
        }
        List<String> out = new ArrayList<>(found);
        // `base` is the package's unadorned default and belongs first so an unset preference lands on it.
        if (out.remove("base")) out.add(0, "base");
        return List.copyOf(out);
    }
}
