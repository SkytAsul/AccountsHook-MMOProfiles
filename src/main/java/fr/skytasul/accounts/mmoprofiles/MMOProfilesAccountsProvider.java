package fr.skytasul.accounts.mmoprofiles;

import fr.phoenixdevt.profiles.ProfileDataModule;
import fr.phoenixdevt.profiles.ProfileList;
import fr.phoenixdevt.profiles.ProfileProvider;
import fr.phoenixdevt.profiles.event.ProfileCreateEvent;
import fr.phoenixdevt.profiles.event.ProfileEvent;
import fr.phoenixdevt.profiles.event.ProfileSelectEvent;
import fr.phoenixdevt.profiles.event.ProfileUnloadEvent;
import fr.skytasul.accounts.AbstractAccountsProvider;
import fr.skytasul.accounts.Account;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import java.util.*;

public class MMOProfilesAccountsProvider extends AbstractAccountsProvider {

	private final ProfileProvider profileProvider;
	private final MMOProfilesHook plugin;

	private final Map<UUID, UUID> profileToPlayerCache = new HashMap<>();

	public MMOProfilesAccountsProvider(MMOProfilesHook plugin) {
		super("mmoprofiles_bridge");
		this.plugin = plugin;

		profileProvider = Bukkit.getServicesManager().getRegistration(ProfileProvider.class).getProvider();
		profileProvider.registerModule(new MMODataModule());
	}

	@Override
	protected @NotNull Optional<Account> getFromIdentifierInternal(@NotNull String accountIdentifier) {
		UUID profileUuid = UUID.fromString(accountIdentifier);

		// ProfileAPI does not allow to directly get the player from a profile UUID,
		// so we have to store a cache.
		UUID playerUuid = profileToPlayerCache.get(profileUuid);
		if (playerUuid == null)
			return Optional.empty();
		return Optional.of(new ProfileAccount(profileUuid, profileProvider.getPlayerData(playerUuid)));
	}

	@Override
	public @NotNull Optional<Account> getCurrentAccountInternal(@NotNull Player p) {
		var profileList = profileProvider.getPlayerData(p.getUniqueId());
		var currentProfile = profileList.getCurrent();
		if (currentProfile == null)
			return Optional.empty();
		return Optional.of(new ProfileAccount(currentProfile.getUniqueId(), profileList));
	}

	@Override
	public @NotNull Collection<@NotNull ? extends Account> getAllAccounts(@NotNull OfflinePlayer player) {
		var profileList = profileProvider.getPlayerData(player.getUniqueId());
		return profileList.getProfiles().stream()
				.map(profile -> new ProfileAccount(profile.getUniqueId(), profileList))
				.toList();
	}

	class MMODataModule implements ProfileDataModule {

		private List<UUID> pendingCreation = new ArrayList<>();

		@Override
		public @NotNull JavaPlugin getOwningPlugin() {
			return plugin;
		}

		@Override
		public @NotNull NamespacedKey getId() {
			return new NamespacedKey(plugin, "bridge");
		}

		private ProfileAccount getAccount(ProfileEvent event) {
			return new ProfileAccount(event.getProfile().getUniqueId(), event.getPlayerData());
		}

		@EventHandler
		public void onProfileCreate(ProfileCreateEvent event) {
			// At this point, ProfileList#getCurrent() returns null. Thus we wait until
			// ProfileSelectEvent which comes directly after to call the AccountsHook events.
			pendingCreation.add(event.getProfile().getUniqueId());
			event.validate(this);
		}

		@EventHandler
		public void onProfileSelect(ProfileSelectEvent event) {
			boolean isCreation = pendingCreation.remove(event.getProfile().getUniqueId());
			profileToPlayerCache.put(event.getProfile().getUniqueId(), event.getPlayer().getUniqueId());
			callAccountJoin(event.getPlayer(), getAccount(event), isCreation);
			event.validate(this);
		}

		@EventHandler
		public void onProfileUnload(ProfileUnloadEvent event) {
			callAccountLeave(event.getPlayer(), getAccount(event));
			event.validate(this);
		}

	}

	class ProfileAccount extends Account {

		private final UUID profileId;
		private final ProfileList profileList;

		public ProfileAccount(UUID profileId, ProfileList profileList) {
			super(MMOProfilesAccountsProvider.this);
			this.profileId = profileId;
			this.profileList = profileList;
		}

		@Override
		public @NotNull OfflinePlayer getOfflinePlayer() {
			return profileList.getPlayer();
		}

		@Override
		public boolean isCurrent() {
			var currentProfile = profileList.getCurrent();
			if (currentProfile == null)
				return false;
			return currentProfile.getUniqueId().equals(profileId);
		}

		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof ProfileAccount other))
				return false;

			return other.profileId.equals(profileId);
		}

		@Override
		public int hashCode() {
			return profileId.hashCode();
		}

		@Override
		protected @NotNull String getAccountIdentifier() {
			return profileId.toString();
		}

	}

}
