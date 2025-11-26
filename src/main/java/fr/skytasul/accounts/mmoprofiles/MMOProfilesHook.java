package fr.skytasul.accounts.mmoprofiles;

import fr.skytasul.accounts.AccountsProvider;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public class MMOProfilesHook extends JavaPlugin{

	@Override
	public void onEnable() {
		MMOProfilesAccountsProvider accountsProvider = new MMOProfilesAccountsProvider(this);
		Bukkit.getServicesManager().register(AccountsProvider.class, accountsProvider, this, ServicePriority.Normal);
	}

}
