package me.moteloff.functionalplugin;

import me.moteloff.bossapi.BossManager;
import me.moteloff.bossapi.ConfigBosses;
import me.moteloff.bossapi.abilities.Ability;
import me.moteloff.bossapi.utils.EntityBuilder;
import me.moteloff.functionalplugin.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import java.awt.Rectangle;

import java.util.ArrayList;
import java.util.List;

import static org.bukkit.entity.EntityType.PLAYER;

public final class FunctionalPlugin extends JavaPlugin {

    private static FunctionalPlugin instance;
    @Override
    public void onEnable() {
        instance = this;
        Bukkit.getScheduler().runTaskLater(this, this::summoner, 100L);
        Bukkit.getScheduler().runTaskLater(this, this::destroyer, 100L);
    }

    public static FunctionalPlugin getInstance() {
        return instance;
    }

    public void summoner() {
        Ability first = new Ability(
                (entity -> true),
                (entity) -> {
                    System.out.println("first");
                    for (int i = 0; i <= 3; i++) {
                        Zombie zomb = (Zombie) new EntityBuilder(EntityType.ZOMBIE).setHealth(30).setSpawnLocation(entity.getLocation()).setSpeed(0.4).spawn();
                        zomb.setBaby();
                    }
                },
                60 * 1000
        );
        Ability second = new Ability(
                (entity -> true),
                (entity) -> {
                    System.out.println("second");
                    ItemStack[] armorContents =entity.getEquipment().getArmorContents();
                    List<Entity> nearbyEntities = entity.getNearbyEntities(7, 5, 7);
                    for (Entity nearbyEntity : nearbyEntities) {
                        if (nearbyEntity instanceof Player) {
                            Player player = (Player) nearbyEntity;
                            Vector awayFromMob = player.getLocation().toVector().subtract(entity.getLocation().toVector()).normalize();
                            awayFromMob.multiply(2).setY(0.5);
                            player.setVelocity(awayFromMob);

                            first.setCooldownTime(60*1000*3);
                        }
                    }
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        entity.getEquipment().setArmorContents(armorContents);
                        entity.getEquipment().setItemInMainHand(new ItemBuilder(Material.BONE,1).build());
                    }, 5*60*1000);

                    entity.getEquipment().setItemInMainHand(new ItemBuilder(Material.STONE_SWORD, 1).addEnchantment(Enchantment.DAMAGE_ALL, 3).build());
                    entity.getEquipment().setHelmet(new ItemBuilder(Material.LEATHER_HELMET, 1).build());
                    entity.getEquipment().setChestplate(new ItemBuilder(Material.LEATHER_CHESTPLATE, 1).build());
                    entity.getEquipment().setLeggings(new ItemBuilder(Material.LEATHER_LEGGINGS, 1).build());
                    entity.getEquipment().setBoots(new ItemBuilder(Material.LEATHER_BOOTS, 1).build());

                },
                60 * 1000 * 10
        );

        BossManager.register(ConfigBosses.getConfigBoss(EntityType.ZOMBIE, "summoner")
                .setWeapon(new ItemBuilder(Material.BONE,1).build())
                .onHit( event -> { event.getEntity().sendMessage("hit"); })
                .onDamaged( (event)-> {event.getEntity().sendMessage("damage");})
                .setSpeed(.4)
                .setArmor(10)
                .setDamage(20)
                .targets(PLAYER, EntityType.OCELOT, EntityType.WOLF)
                .ignoredDamageType(EntityDamageEvent.DamageCause.MAGIC, EntityDamageEvent.DamageCause.PROJECTILE, EntityDamageEvent.DamageCause.POISON)
                .abilities(first, second));
    }

    public void destroyer() {
        Ability first = new Ability(
                (entity -> entity.getHealth() <= entity.getMaxHealth()/2),
                (entity) -> {
                    entity.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 999999, 1));
                    entity.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_AXE));
                },
                60 * 1000
        );
        Ability second = new Ability(
                (entity -> entity.getHealth() <= entity.getMaxHealth()/2),
                (entity) -> {doLeap(entity, getNearestPlayer(entity), 1, 1);},
                60 * 1000);
        BossManager.register(ConfigBosses.getConfigBoss(EntityType.PILLAGER, "destroyer")
                .setWeapon(new ItemBuilder(Material.CROSSBOW, 1).addEnchantment(Enchantment.PIERCING, 3).addEnchantment(Enchantment.MULTISHOT, 0).build())
                .onHit( event -> { event.getEntity().sendMessage("hit"); })
                .setSpeed(1)
                .setArmor(10)
                .setDamage(20)
                .onDamaged( (event)-> {
                    AttributeInstance knockbackResistance = ((LivingEntity) event.getEntity()).getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE);
                    if (knockbackResistance != null) {
                        knockbackResistance.setBaseValue(1.0);
                    }
                })
                .abilities(first, second));
    }
    private void doLeap(Entity mob, Entity target, double speed, double distance) {
        Location mobLocation = mob.getLocation();
        Block targetBlock = target.getLocation().getBlock();
        Mob mob1 = (Mob) mob;
        mob1.setTarget((LivingEntity) target);

        Vector direction = targetBlock.getLocation().subtract(mobLocation).toVector();

        direction.normalize().multiply(distance);

        double step = 0.5;
        BoundingBox mobBox = mob.getBoundingBox();
        for (double i = 0; i < distance; i += step) {
            Location loc = mobLocation.clone().add(direction.clone().multiply(i / distance));
            BoundingBox blockBox = targetBlock.getBoundingBox();
            if (mobBox.shift(loc.toVector()).overlaps(blockBox)) {
                return;
            }
        }

        mob.setVelocity(direction.clone().normalize().multiply(speed));
    }

    private Player getNearestPlayer(Entity mob) {
        double closestDistance = Double.MAX_VALUE;
        Player closestPlayer = null;

        for (Player player : Bukkit.getOnlinePlayers()) {
            double distance = player.getLocation().distance(mob.getLocation());
            if (distance < closestDistance) {
                closestDistance = distance;
                closestPlayer = player;
            }
        }

        return closestPlayer;
    }

}
