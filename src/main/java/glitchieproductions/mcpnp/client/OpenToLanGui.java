package glitchieproductions.mcpnp.client;

import glitchieproductions.mcpnp.common.Mcpnp;
import io.github.cottonmc.cotton.gui.client.CottonClientScreen;
import io.github.cottonmc.cotton.gui.client.LightweightGuiDescription;
import io.github.cottonmc.cotton.gui.widget.WButton;
import io.github.cottonmc.cotton.gui.widget.WGridPanel;
import io.github.cottonmc.cotton.gui.widget.WLabel;
import io.github.cottonmc.cotton.gui.widget.WTextField;
import io.github.cottonmc.cotton.gui.widget.WToggleButton;
import io.github.cottonmc.cotton.gui.widget.data.HorizontalAlignment;
import io.github.cottonmc.cotton.gui.widget.data.VerticalAlignment;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.text.TranslatableText;

public class OpenToLanGui extends LightweightGuiDescription {
	private final Mcpnp.Config cfg;

	private OpenToLanGui(Screen parent) {
		MinecraftClient client = MinecraftClient.getInstance();
		IntegratedServer server = client.getServer();
		this.cfg = Mcpnp.getConfig(server);

		if (cfg.needsDefaults) {
			cfg.portForward = false;
			cfg.allowCheats = server.getPlayerManager().areCheatsAllowed();
			cfg.gameMode = server.getSaveProperties().getLevelInfo().getGameMode().getId();
			cfg.needsDefaults = false;
		}

		WGridPanel main = new WGridPanel();
		setRootPanel(main);

		WTextField portField;

		{
			final int x = 0;
			int y = -1;

			{
				WLabel connectionLabel = new WLabel(new TranslatableText("mcpnp.gui.title")).setHorizontalAlignment(HorizontalAlignment.LEFT).setVerticalAlignment(VerticalAlignment.CENTER);
				main.add(connectionLabel, x, ++y);
			}

			{
				WLabel portLabel = new WLabel(new TranslatableText("mcpnp.gui.port")).setVerticalAlignment(VerticalAlignment.CENTER);
				main.add(portLabel, x, ++y);
				portField = new WTextField(new TranslatableText("mcpnp.gui.port"));
				portField.setMaxLength(5);
				portField.setText(String.valueOf(cfg.port));
				portField.validate(this);
				main.add(portField, x, ++y, 3, 1);
			}

			{
				WToggleButton useUpnp = new WToggleButton(new TranslatableText("mcpnp.gui.useupnp"));
				useUpnp.setToggle(cfg.portForward);
				useUpnp.setOnToggle(b -> cfg.portForward = b);
				main.add(useUpnp, x, ++y);
			}

			{
				WToggleButton copyToClipboard = new WToggleButton(new TranslatableText("mcpnp.gui.copyip"));
				copyToClipboard.setToggle(cfg.copyToClipboard);
				copyToClipboard.setOnToggle(b -> cfg.copyToClipboard = b);
				main.add(copyToClipboard, x, ++y);
			}

			{
				WLabel gameModeLabel = new WLabel(new TranslatableText("mcpnp.gui.othergamesettings")).setHorizontalAlignment(HorizontalAlignment.LEFT).setVerticalAlignment(VerticalAlignment.CENTER);
				main.add(gameModeLabel, x, ++y);

			}

			{
				WToggleButton allowCheats = new WToggleButton(new TranslatableText("mcpnp.gui.allowcheats"));
				allowCheats.setToggle(cfg.allowCheats);
				allowCheats.setOnToggle(b -> cfg.allowCheats = b);
				main.add(allowCheats, x, ++y);
			}

			{
				WButton openToLan = new WButton(new TranslatableText("mcpnp.gui.opentolan"));
				openToLan.setOnClick(() -> {
					int port;
					try {
						port = Integer.parseInt(portField.getText());
						if (port <= 1024) {
							portField.setText("");
							portField.setSuggestion("mcpnp.gui.portsmall");
							portField.onClick(0, 0, 0);
							return;
						} else if (port > 65535) {
							portField.setText("");
							portField.setSuggestion("mcpnp.gui.portlarge");
							portField.onClick(0, 0, 0);
							return;
						}
					} catch (NumberFormatException ex) {
						portField.setText("");
						portField.setSuggestion("mcpnp.gui.port");
						portField.onClick(0, 0, 0);
						return;
					}
					cfg.port = port;
					client.openScreen(null);
					Mcpnp.openToLan(server, null);
					client.inGameHud.getChatHud().addMessage(new TranslatableText("mcpnp.text.port", port));
				});

				main.add(openToLan, x, 8, 5, 1);
			}
		}

		final int rightX = 9;
		int rightY = 0;

		//region GameMode
		{
			WLabel gameModeLabel = new WLabel(new TranslatableText("mcpnp.gui.gamemode")).setHorizontalAlignment(HorizontalAlignment.LEFT).setVerticalAlignment(VerticalAlignment.CENTER);
			main.add(gameModeLabel, rightX, rightY);
			WToggleButton survival = new WToggleButton(new TranslatableText("mcpnp.gui.survival"));
			main.add(survival, rightX, rightY += 1);
			WToggleButton creative = new WToggleButton(new TranslatableText("mcpnp.gui.creative"));
			main.add(creative, rightX, rightY += 1);
			WToggleButton adventure = new WToggleButton(new TranslatableText("mcpnp.gui.adventure"));
			main.add(adventure, rightX, rightY += 1);
			WToggleButton spectator = new WToggleButton(new TranslatableText("mcpnp.gui.spectator"));
			main.add(spectator, rightX, rightY += 1);

			gameModeButtonMagic(0, survival, creative, adventure, spectator);
			gameModeButtonMagic(1, creative, survival, adventure, spectator);
			gameModeButtonMagic(2, adventure, survival, creative, spectator);
			gameModeButtonMagic(3, spectator, creative, survival, adventure);
		}

		//endregion


		WButton cancel = new WButton(new TranslatableText("mcpnp.gui.cancel"));
		cancel.setOnClick(() -> client.openScreen(parent));
		main.add(cancel, 9, 8, 5, 1);
	}

	private void gameModeButtonMagic(int gameMode, WToggleButton me, WToggleButton... others) {
		if (cfg.gameMode == gameMode) {
			me.setToggle(true);
		}

		me.setOnToggle(b -> {
			if (b) {
				this.cfg.gameMode = gameMode;
				for (WToggleButton it : others) {
					it.setToggle(false);
				}
			} else {
				me.setToggle(true);
			}
		});
	}

	public static class GuiScreen extends CottonClientScreen {
		public GuiScreen(Screen parent) {
			super(new OpenToLanGui(parent));
		}
	}
}
