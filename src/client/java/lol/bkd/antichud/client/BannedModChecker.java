package lol.bkd.antichud.client;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public class BannedModChecker {
    private static final Set<String> BANNED_MODS = Set.of(
            "undertale-healthbars",
            "healthbars",
            "mr_health_indicator",
            "healthindicatortxf",
            "healthindicators",
            "wurst",
            "wurstplus",
            "impact",
            "meteor",
            "future",
            "liquidbounce",
            "zeal",
            "aristois",
            "nodus",
            "cheatbreaker",
            "vape",
            "sigma",
            "horion",
            "flux",
            "avolition",
            "kings",
            "enders",
            "rise",
            "zeroday",
            "paragon",
            "obsidian",
            "melon",
            "huzuni",
            "weepcraft",
            "novoline",
            "phantom",
            "sense",
            "astra",
            "nebula",
            "vapor",
            "trinity",
            "exodus",
            "aqua",
            "celestial",
            "void",
            "nemesis",
            "inertia",
            "momentum",
            "velocity",
            "acceleration",
            "quantum",
            "particle",
            "wave",
            "pulse",
            "signal",
            "frequency",
            "amplitude",
            "resonance",
            "harmonic",
            "oscillation",
            "vibration",
            "tremor",
            "quake",
            "seismic",
            "tectonic",
            "volcanic",
            "magma",
            "lava",
            "pyroclastic",
            "eruption",
            "explosive",
            "detonation",
            "blast",
            "shockwave",
            "overpressure",
            "fragmentation",
            "shrapnel",
            "debris",
            "fallout",
            "radiation",
            "contamination",
            "hazardous",
            "toxic",
            "lethal",
            "deadly",
            "fatal",
            "mortality",
            "death",
            "destruction",
            "annihilation",
            "obliteration",
            "extinction",
            "termination",
            "elimination",
            "eradication",
            "extermination"
    );

    public static void check(UUID playerUuid, String username) {
        FabricLoader loader = FabricLoader.getInstance();
        Collection<ModContainer> mods = loader.getAllMods();

        for (ModContainer mod : mods) {
            String modId = mod.getMetadata().getId();
            if (BANNED_MODS.contains(modId.toLowerCase())) {
                LogSender.sendBannedModReport(playerUuid, username, modId);
            }
        }
    }
}