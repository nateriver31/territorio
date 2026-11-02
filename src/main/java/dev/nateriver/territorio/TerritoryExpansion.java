package dev.nateriver.territorio;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import java.util.*;

/** Immutable snapshot: TAB may resolve placeholders from an asynchronous thread. */
public final class TerritoryExpansion extends PlaceholderExpansion {
    private final Domain domain;
    private volatile Map<UUID, Values> snapshot=Map.of();
    private record Values(String tag,String group,String role,String symbol){}
    public TerritoryExpansion(Domain domain){this.domain=domain;refresh();}
    /** Call only on the server thread, after group mutations. */
    public void refresh(){
        Map<UUID,Values> next=new HashMap<>();
        for(Domain.Group group:domain.s.groups.values())for(Domain.Member member:group.members.values())next.put(member.player,new Values(group.tag,group.name,Commands.roleName(member.role),roleSymbol(member.role)));
        snapshot=Map.copyOf(next);
    }
    @Override public String getIdentifier(){return "territorio";}
    @Override public String getAuthor(){return "NateRiver";}
    @Override public String getVersion(){return "2.0.2";}
    @Override public boolean persist(){return true;}
    @Override public String onRequest(OfflinePlayer player,String parameter){
        Values values=player==null?null:snapshot.get(player.getUniqueId());
        return switch(parameter.toLowerCase(Locale.ROOT)){
            case "tag"->values==null?"":values.tag;
            case "tag_formatada"->values==null?"":"§6["+values.tag+"]§r ";
            case "grupo"->values==null?"":values.group;
            case "cargo"->values==null?"":values.role;
            case "cargo_simbolo"->values==null?"":values.symbol;
            case "prefixo_minimessage"->values==null?"":MiniMessage.miniMessage().serialize(
                    LegacyComponentSerializer.legacySection().deserialize(values.symbol+"§6["+values.tag+"]§r "));
            case "tem_grupo"->Boolean.toString(values!=null);
            default->null;
        };
    }
    private static String roleSymbol(Domain.Role role){
        return switch(role){case LEADER->"§6♛§r ";case COLEADER->"§6◆§r ";case OFFICER->"§6•§r ";case MEMBER->"";};
    }
}
