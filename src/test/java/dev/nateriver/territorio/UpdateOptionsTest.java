package dev.nateriver.territorio;

import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UpdateOptionsTest {
    @Test void oldConfigWithoutNewKeysUsesSafeAdditiveDefaults(){
        var options=UpdateOptions.read(new YamlConfiguration());
        assertTrue(options.bonus());assertTrue(options.bonusPvp());assertEquals(6,options.perMember());
        assertEquals(.25,options.chance());assertTrue(options.normalIntel());assertTrue(options.bonusIntel());
        assertFalse(options.personalIntel());assertEquals("",options.channel());assertEquals(30,options.retentionDays());
    }
    @Test void rejectsInvalidOptionsRatherThanCoercingOrClamping(){
        Map<String,List<?>> invalid=Map.of(
            "claim-bonus.max-per-member",List.of(0,-1,2.5,"6",Double.NaN,Double.POSITIVE_INFINITY,Long.MAX_VALUE),
            "logs.retention-days",List.of(0,-10,1.5,"30"),
            "intel-book.drop-chance",List.of(-.1,1.1,"0.25",Double.NaN,Double.POSITIVE_INFINITY),
            "claim-bonus.enabled",List.of("yes",1,"false"),
            "claim-bonus.pvp.enabled",List.of("no",0),
            "discordsrv.channel",List.of(123,"global\nnew-line","x".repeat(129)));
        invalid.forEach((key,values)->values.forEach(value->{var config=new YamlConfiguration();config.set(key,value);assertThrows(IllegalArgumentException.class,()->UpdateOptions.read(config),key+"="+value);}));
    }
    @Test void changedSafeCapacityAndDecimalChanceAreAcceptedWithoutGivingFreeBalance(){
        var config=new YamlConfiguration();config.set("claim-bonus.max-per-member",8);config.set("intel-book.drop-chance",.255);config.set("discordsrv.channel","  global  ");
        var options=UpdateOptions.read(config);Domain d=new Domain();var group=d.create(UUID.randomUUID(),"Roma","ROMA");
        options.validateCapacity(d);assertEquals(0,group.bonusEarned);assertEquals(8,options.perMember());assertEquals("global",options.channel());
        var plain=net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
        String manual=Commands.manualPages(9,9,options.perMember(),options.chance()).stream().map(plain::serialize).reduce("",String::concat);
        assertTrue(manual.contains("8 bônus"));assertTrue(manual.contains("25.5%"));
    }
}
