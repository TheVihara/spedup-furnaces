package me.vihara.plugin.spedupfurnaces.core;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import me.vihara.plugin.spedupfurnaces.api.ISpedupFurnace;
import org.bukkit.Material;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SpedupFurnace implements ISpedupFurnace {
    Material material;
    int level;

    @Override
    public int getLevel() {
        return 0;
    }

    @Override
    public void setLevel(int level) {

    }
}
