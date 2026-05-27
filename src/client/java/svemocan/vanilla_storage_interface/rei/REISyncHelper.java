package svemocan.vanilla_storage_interface.rei;

import me.shedaniel.rei.api.client.REIRuntime;
import me.shedaniel.rei.api.client.search.SearchProvider;

public class REISyncHelper {
    public static void setSearch(String query) {
        try {
            // SearchProvider.getInstance().setSearch(query);
            REIRuntime.getInstance().getSearchTextField().setText(query);
        } catch (Throwable t) {
            // Failsafe if REI is not installed or errors
        }
    }
}