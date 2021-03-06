package com.edtheloon.gold2economy;

import java.util.HashMap;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import com.feildmaster.lib.configuration.EnhancedConfiguration;

public class Commands implements CommandExecutor {

	private gold2economy plugin = null; // Added by turt2live
	private VaultSupport vault = null; // Added by turt2live

	public Commands(gold2economy plugin, VaultSupport vault){ // Added by turt2live
		this.plugin = plugin;
		this.vault = vault;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args){
		// Initialize variables (turt2live)
		EnhancedConfiguration config = plugin.getConfig();

		// Reload configuration to not cause issues (turt2live)
		config.load();

		// First check to see whether our plugin is 'enabled' and Register has a payment method
		if(gold2economy.enabled){ // Modified by turt2live (removed && Methods.hasMethod()
			// because the if should not continue of the plugin is not enabled

			// COMMAND - /gi
			if(cmd.getName().equalsIgnoreCase("gi")){

				// Command = /gi - Shows the help menu
				if(args.length == 0){
					return false;
				}

				// Check for buy command
				if(args[0].equalsIgnoreCase("buy")){
					if(args.length < 3){
						return false;
					}
					if(!(sender instanceof Player)){
						sender.sendMessage(ChatColor.RED + "Sorry! Only players can buy things");
						return true;
					}
					return buy(args[1], args[2], (Player) sender);
				}

				// Command = /gi rates - Tells player the conversion rate
				if(args.length == 1 && args[0].equalsIgnoreCase("rates")){
					if(vault.isActive()){
						Functions.displayRates(sender, vault, config); // Fixed for argument change (turt2live)
						return true;
					}else{
						sender.sendMessage(ChatColor.RED + "Gold2Economy was unable to find a supported economy plugin");
						return true;
					}
				}

				// Command = /gi reload - Reload configuration
				if(args.length == 1 && args[0].equalsIgnoreCase("reload")){
					if(sender instanceof ConsoleCommandSender){
						plugin.api.reloadConfig(sender); // removed old call, replaced with newer one (turt2live)
						return true;
					}else if(vault.hasPermission(sender, gold2economy.PERMISSION_ADMIN)){ // Fixed for argument change (turt2live)
						plugin.api.reloadConfig(sender); // removed old call, replaced with newer one (turt2live)
						return true;
					}else{
						sender.sendMessage(ChatColor.RED + "You don't have permission to do this!");
						return true;
					}
				}

				// Command = /gi <item> all - Convert all <item> - <item> is either iron, gold or diamond
				if(args.length == 2 && args[1].equalsIgnoreCase("all") && sender instanceof Player){
					return remove(args[0], -1, (Player) sender);
				}

				// Command = /gi <item> <amount> - Convert <amount> of <item> - <item> is either iron, gold, diamond, or custom
				// If <amount> is left empty it will convert 1 of the item
				if(args.length >= 1 && sender instanceof Player){
					// Use a try-catch to safely retrieve amount to convert
					int amount = 0;
					try{
						if(args.length == 2){
							amount = Integer.parseInt(args[1]);
						}else if(args.length == 1){
							amount = 1;
						}
					}catch(NumberFormatException e){
						// DEBUG LINE
						gold2economy.log.severe("[" + vault.getPlugin().getDescription().getName() + "] Error: " + e.toString());
						return false;
					}
					return remove(args[0], amount, (Player) sender);
				}

			}
		}else if(gold2economy.enabled){ // This part will run if the plugin is not 'enabled'
			sender.sendMessage(ChatColor.RED + "Gold2Economy is disabled because no currency sytem was found");
			return true;
		}else{
			sender.sendMessage(ChatColor.RED + "Gold2Economy is disabled until Vault is enabled.");
			return true;
		}
		return false;
	}

	@SuppressWarnings ("deprecation")
	public boolean buy(String strItemID, String strAmount, Player player){
		EnhancedConfiguration config = plugin.getConfig();
		boolean convertIron = config.getBoolean("buy.iron");
		boolean convertGold = config.getBoolean("buy.gold");
		boolean convertDiamond = config.getBoolean("buy.diamond");
		String permission = "Gold2Economy.null";
		int itemID = 0;
		// Determine what itemID to use
		if(strItemID.equalsIgnoreCase("iron")){
			itemID = 265;
			permission = gold2economy.PERMISSION_IRON;
		}else if(strItemID.equalsIgnoreCase("gold")){
			itemID = 266;
			permission = gold2economy.PERMISSION_GOLD;
		}else if(strItemID.equalsIgnoreCase("diamond")){
			itemID = 264;
			permission = gold2economy.PERMISSION_DIAMOND;
		}else{
			try{
				itemID = Integer.parseInt(strItemID);
				if(Converter.isAllowed(itemID)){
					permission = "Gold2Economy." + itemID;
				}
			}catch(Exception e){
				player.sendMessage(ChatColor.RED + "That is not a valid item ID!");
				return true;
			}
		}

		// Don't continue if server config says we can't convert item
		// Change this from a switch (itemID) to if conditions because it was causing bugs
		if(itemID == 265){ // IRON
			if(!convertIron){
				player.sendMessage(ChatColor.RED + "This server doesn't allow iron to be bought");
				return true;
			}
		}else if(itemID == 266){ // GOLD
			if(!convertGold){
				player.sendMessage(ChatColor.RED + "This server doesn't allow gold to be bought");
				return true;
			}
		}else if(itemID == 264){ // DIAMOND
			if(!convertDiamond){
				player.sendMessage(ChatColor.RED + "This server doesn't allow diamond to be bought");
				return true;
			}
		}else if(!Converter.canBuy(itemID)){
			player.sendMessage(ChatColor.RED + "This server doesn't allow that to be bought! ");
			return true;
		}

		// Don't continue if player doesn't have required permission
		if(!vault.hasPermission(player, permission)){ // Fixed for argument change (turt2live)
			player.sendMessage(ChatColor.RED + "You can't buy that!");
			return true;
		}

		// Check number
		double amount = 0.0;
		try{
			amount = Double.parseDouble(strAmount);
		}catch(Exception e){
			player.sendMessage(ChatColor.RED + "'" + strAmount + "' is not a number!");
			return true;
		}

		double balance = vault.balance(player.getName());
		// Setup variables (turt2live)
		double ironRate = config.getDouble("rates.iron");
		double goldRate = config.getDouble("rates.gold");
		double diamondRate = config.getDouble("rates.diamond");

		// Declare local variables
		Double conversion = 0.0;
		PlayerInventory pi = player.getInventory();

		// Calculate conversion rate
		// Change this from a switch (itemID) to if conditions because it was causing bugs
		if(itemID == 265){
			conversion = ironRate * amount; // Fixed for argument change (turt2live)
		}else if(itemID == 266){
			conversion = goldRate * amount; // Fixed for argument change (turt2live)
		}else if(itemID == 264){
			conversion = diamondRate * amount; // Fixed for argument change (turt2live)
		}else if(Converter.canBuy(itemID)){ // For customized/other items (like gold nuggets) - Turt2Live
			conversion = Converter.getBuyRate(itemID) * amount;
		}

		if(conversion > balance){
			player.sendMessage(ChatColor.RED + "You can't afford that! " + ChatColor.GRAY + "(Cost: " + vault.format(conversion) + ", Balance: " + vault.format(balance) + ")");
			return true;
		}

		// Add items to inventory
		boolean room = true;
		int freeSpace = 0;
		for(ItemStack item : pi.getContents()){
			if(item != null){
				if(item.getTypeId() == itemID){
					if(item.getAmount() < item.getMaxStackSize()){
						freeSpace += item.getMaxStackSize() - item.getAmount();
					}
				}
			}else{
				freeSpace += 64;
			}
		}
		if(freeSpace >= amount){
			double left = amount;
			int index = 0;
			HashMap<Integer, ItemStack> add = new HashMap<Integer, ItemStack>();
			for(ItemStack item : pi.getContents()){
				if(item != null){
					if(item.getTypeId() == itemID){
						double camount = item.getAmount();
						if(camount < 64){
							double mdiff = 64 - camount;
							if(mdiff > left){
								mdiff = left;
							}
							left -= mdiff;
							item.setAmount((int) (camount + mdiff));
						}
					}else if(item.getType() == Material.AIR){
						item = new ItemStack(itemID, (int) (left >= 64 ? 64 : left));
						left -= (int) (left >= 64 ? 64 : left);
					}
				}else{
					item = new ItemStack(itemID, (int) (left >= 64 ? 64 : left));
					left -= (int) (left >= 64 ? 64 : left);
					add.put(index, item);
				}
				if(left <= 0){
					break;
				}
				index++;
			}
			for(Integer i : add.keySet()){
				pi.setItem(i, add.get(i));
			}
		}else{
			room = false;
		}

		// Withdraw and alert
		if(room){
			player.getInventory().setContents(pi.getContents());
			vault.withdraw(player.getName(), conversion);
			player.sendMessage(ChatColor.GREEN + "Item" + (amount > 1 ? "s" : "") + " bought! Your new balance is " + vault.format(vault.balance(player.getName())));
		}else{
			player.sendMessage(ChatColor.RED + "You have no inventory space for that!");
		}
		player.updateInventory();
		return true;
	}

	public boolean remove(String strItemID, Integer amount, Player player){
		EnhancedConfiguration config = plugin.getConfig();
		boolean convertIron = config.getBoolean("convert.iron");
		boolean convertGold = config.getBoolean("convert.gold");
		boolean convertDiamond = config.getBoolean("convert.diamond");
		String permission = "Gold2Economy.null";
		int itemID = 0;
		// Determine what itemID to use
		if(strItemID.equalsIgnoreCase("iron")){
			itemID = 265;
			permission = gold2economy.PERMISSION_IRON;
		}else if(strItemID.equalsIgnoreCase("gold")){
			itemID = 266;
			permission = gold2economy.PERMISSION_GOLD;
		}else if(strItemID.equalsIgnoreCase("diamond")){
			itemID = 264;
			permission = gold2economy.PERMISSION_DIAMOND;
		}else{
			try{
				itemID = Integer.parseInt(strItemID);
				if(Converter.isAllowed(itemID)){
					permission = "Gold2Economy." + itemID;
				}
			}catch(Exception e){
				player.sendMessage(ChatColor.RED + "That is not a valid item ID!");
				return true;
			}
		}

		// Don't continue if server config says we can't convert item
		// Change this from a switch (itemID) to if conditions because it was causing bugs
		if(itemID == 265){ // IRON
			if(!convertIron){
				player.sendMessage(ChatColor.RED + "This server doesn't allow iron to be converted");
				return true;
			}
		}else if(itemID == 266){ // GOLD
			if(!convertGold){
				player.sendMessage(ChatColor.RED + "This server doesn't allow gold to be converted");
				return true;
			}
		}else if(itemID == 264){ // DIAMOND
			if(!convertDiamond){
				player.sendMessage(ChatColor.RED + "This server doesn't allow diamond to be converted");
				return true;
			}
		}else if(!Converter.isAllowed(itemID)){
			player.sendMessage(ChatColor.RED + "This server doesn't allow that to be converted! ");
			return true;
		}

		// Don't continue if player doesn't have required permission
		if(!vault.hasPermission(player, permission)){ // Fixed for argument change (turt2live)
			player.sendMessage(ChatColor.RED + "You can't convert that!");
			return true;
		}

		// Prepare to loop through player inventory
		PlayerInventory pi = player.getInventory();
		ItemStack items[] = pi.getContents();

		// First check to see that user has at least 1 of <item>
		if(pi.contains(itemID)){

			if(amount == -1){
				// Loop through player's inventory to look for <item>
				amount = 0; // Reset to zero to remove the "one remaining" bug
				for(ItemStack item : items){
					// if the inventory slot is not null AND it is the item we are looking for then change amount
					if(item != null){
						if(item.getTypeId() == itemID){
							amount += item.getAmount();
						}
					}
				}
			}
			Converter.convertItem(player, itemID, amount, vault, config); // Added by turt2live & fixed for argument change
			return true; // finally say we've handled command correctly

		}else{
			player.sendMessage(ChatColor.RED + "You don't have any of that item!");
			return true;
		}
	}
}
