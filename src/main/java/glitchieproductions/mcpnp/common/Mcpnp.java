package glitchieproductions.mcpnp.common;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.Clipboard;
import net.minecraft.client.util.NetworkUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.text.LiteralText;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.GameMode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bitlet.weupnp.GatewayDevice;
import org.bitlet.weupnp.GatewayDiscover;
import org.bitlet.weupnp.PortMappingEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

public class Mcpnp implements ModInitializer {
	public static final String MODID = "mcpnp";
	private static final Map<MinecraftServer, Config> configMap = Collections.synchronizedMap(new WeakHashMap<>());
	private static final Gson gson = new GsonBuilder().create();
	private static final Logger LOGGER = LogManager.getLogger(Mcpnp.class);

	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTING.register(this::onServerLoad);
		ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStop);
	}

	@NotNull
	public static Config getConfig(MinecraftServer server) {
		return Objects.requireNonNull(configMap.get(server), "no config for server???");
	}

	public static void openToLan(MinecraftServer server, @Nullable Inet4Address selected) {
		if (!(server instanceof IntegratedServer)) {
			throw new UnsupportedOperationException("todo");
		}

		Config cfg = configMap.get(server);
		saveConfig(cfg);

		if (cfg.portForward) {
			new Thread(() -> {
				try {
					GatewayDiscover discover = new GatewayDiscover();
					Map<InetAddress, GatewayDevice> values = discover.discover();
					if (values.size() > 1) {
						LOGGER.fatal("WARNING: Using first upnp device found, this might not be safe!");
					}
					cfg.device = discover.getValidGateway();
					if (cfg.device == null) {
						sendMessage("Unable to forward port: UPnP is not enabled on your router!", server);
						cfg.portForward = false; // people are lazy
						return;
					}
					String localIp = cfg.device.getLocalAddress().getHostAddress();
					// this is always null
					//Integer amount = device.getPortMappingNumberOfEntries();
					boolean run = true;
					try {
						int i = 0;

						while (true) {
							PortMappingEntry entry = new PortMappingEntry();

							if (!cfg.device.getGenericPortMappingEntry(i++, entry)) {
								break;
							}
							if (entry.getExternalPort() == cfg.port || (entry.getInternalPort() == cfg.port && entry.getInternalClient().equals(localIp))) {
								// silently ignore instances when it's already forwarded properly
								if (!(entry.getPortMappingDescription().equals(MODID) && entry.getInternalClient().equals(localIp))) {
									sendMessage("Unable to forward port: " + entry.getPortMappingDescription() + " has already claimed port " + cfg.port + " for " + entry.getInternalClient(), server);
									run = false;
									break;
								}
							}
						}
					} catch (Exception ex) {
						LOGGER.warn("thrown exception during searching, assuming we're ok");
						LOGGER.warn(ex);
					}

					if (run) {
						if (!cfg.device.addPortMapping(cfg.port, cfg.port, localIp, "tcp", "mcpnp")) {
							sendMessage("Unable to forward port: unknown error while adding port mapping.", server);
						} else {
							// for safety
							Runtime.getRuntime().addShutdownHook(new Thread(() -> {
								try {
									cfg.device.deletePortMapping(cfg.port, "tcp");
								} catch (IOException | SAXException e) {
									LOGGER.throwing(e);
								}
							}));
							sendMessage("Successfully forwarded port.", server);

							if (cfg.copyToClipboard) {
								MinecraftClient client = MinecraftClient.getInstance();
								client.execute(() -> {
									try {
										client.keyboard.setClipboard(cfg.device.getExternalIPAddress() + ":" + cfg.port);
									} catch (IOException | SAXException e) {
										client.inGameHud.getChatHud().addMessage(new LiteralText("Couldn't get external IP?"));
										LOGGER.throwing(e);
									}
									client.inGameHud.getChatHud().addMessage(new LiteralText("Your IP address has been copied to your clipboard."));
								});
							}


						}
					}
				} catch (IOException | ParserConfigurationException | SAXException ex) {
					sendMessage("Unable to forward port: " + ex.getLocalizedMessage(), server);
					LOGGER.throwing(ex);
				}
			}).start();
		}

		server.openToLan(GameMode.byId(cfg.gameMode), cfg.allowCheats, cfg.port);
	}

	private void onServerLoad(MinecraftServer server) {
		Path location = server.getSavePath(WorldSavePath.ROOT).resolve("mcpnp.json");
		Config cfg;

		try {
			// Load a pre-existing config
			cfg = gson.fromJson(new String(Files.readAllBytes(location)), Config.class);
			cfg.location = location;
		} catch (IOException | JsonParseException e) {
			try {
				Files.deleteIfExists(location);
			} catch (IOException ioException) {
				//
			}

			cfg = new Config();
			cfg.location = location;
			cfg.needsDefaults = true;
		}

		configMap.put(server, cfg);
	}

	private void onServerStop(MinecraftServer server) {
		Config cfg = configMap.get(server);

		if (cfg.device != null) {
			try {
				cfg.device.deletePortMapping(cfg.port, "tcp");
			} catch (IOException | SAXException e) {
				throw new IllegalStateException("Could not delete port mapping", e);
			}
		}

		// may as well remove the config even though the WeakHashMap should handle it
		configMap.remove(server);
	}

	private static void saveConfig(Config cfg) {
		if (!cfg.needsDefaults) {
			try {
				Files.write(cfg.location, gson.toJson(cfg).getBytes(), StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
			} catch (IOException e) {
				LOGGER.error("Unable to write config file!", e);
			}
		}
	}

	private static void sendMessage(String string, MinecraftServer server) {
		server.execute(() -> MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(new LiteralText(string)));
	}
	public static class Config {
		public int version = 1; // set by default
		public int port = NetworkUtils.findLocalPort(); // set by default
		public boolean portForward = true; // set by default
		public int gameMode = -1; // will be overwritten
		public boolean allowCheats = false; // will be overwritten
		public boolean copyToClipboard = true; // set by default
		public transient Path location;
		public transient boolean needsDefaults = false;

		// upnp stuff
		@Nullable
		public transient GatewayDevice device;
	}

}
