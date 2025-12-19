package cz.neumimto.towny.townycivs.mechanics.common;

import com.electronwill.nightconfig.core.conversion.Path;
import com.typesafe.config.Optional;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for conditional item production.
 * Defines recipes where specific input items produce specific output items.
 *
 * Example config:
 * Production: [
 *   {
 *     Mechanic: conditional_item_production
 *     Value: {
 *       Recipes: [
 *         {
 *           Input: { Material: "oak_log", Amount: 1, ConsumeItem: true }
 *           Output: { Material: "oak_planks", Amount: 4 }
 *         }
 *         {
 *           Input: { Material: "wooden_axe", Amount: 1, ConsumeItem: false, DamageAmount: 10 }
 *           Output: { Material: "oak_planks", Amount: 3 }
 *         }
 *       ]
 *     }
 *   }
 * ]
 */
public class ConditionalItemList implements Wrapper {

    @Path("Recipes")
    public List<Recipe> recipes = new ArrayList<>();

    @Override
    public boolean isObject() {
        return true;
    }

    /**
     * A single recipe: input item → output item
     */
    public static class Recipe {
        @Path("Input")
        public ItemList.ConfigItem input;

        @Path("Output")
        public ItemList.ConfigItem output;

        @Path("Priority")
        @Optional
        public Integer priority;
    }
}

