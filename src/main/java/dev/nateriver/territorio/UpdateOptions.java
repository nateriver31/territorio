package dev.nateriver.territorio;

import org.bukkit.configuration.file.FileConfiguration;

/** Validated before any running option or state is replaced. */
record UpdateOptions(boolean bonus, int perMember, boolean bonusPvp,
                     boolean intel, double chance, boolean normalIntel, boolean bonusIntel, boolean personalIntel,
                     boolean discord, boolean war, boolean peace, String channel, int retentionDays) {
    static UpdateOptions read(FileConfiguration c) {
        boolean bonus=bool(c,"claim-bonus.enabled",true),pvp=bool(c,"claim-bonus.pvp.enabled",true);
        int max=positive(c,"claim-bonus.max-per-member",6),retention=positive(c,"logs.retention-days",30);
        Object raw=c.get("intel-book.drop-chance",0.25);
        if(!(raw instanceof Number number)||!Double.isFinite(number.doubleValue())||number.doubleValue()<0||number.doubleValue()>1)
            throw new IllegalArgumentException("intel-book.drop-chance deve ser um número entre 0 e 1.");
        Object mapped=c.get("discordsrv.channel","");
        if(!(mapped instanceof String channel)||channel.length()>128||channel.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("discordsrv.channel deve ser um nome lógico válido; vazio usa o canal principal.");
        return new UpdateOptions(bonus,max,pvp,bool(c,"intel-book.enabled",true),number.doubleValue(),
            bool(c,"intel-book.include-normal-claims",true),bool(c,"intel-book.include-bonus-claims",true),bool(c,"intel-book.include-personal-claims",false),
            bool(c,"discordsrv.enabled",true),bool(c,"discordsrv.announce-war",true),bool(c,"discordsrv.announce-peace",true),channel.trim(),retention);
    }
    private static int positive(FileConfiguration c,String key,int fallback) {
        Object raw=c.get(key,fallback);
        if(!(raw instanceof Number n)||n.doubleValue()!=n.intValue()||n.intValue()<1)
            throw new IllegalArgumentException(key+" deve ser um inteiro positivo.");
        return n.intValue();
    }
    private static boolean bool(FileConfiguration c,String key,boolean fallback) {
        Object raw=c.get(key,fallback);
        if(!(raw instanceof Boolean value))throw new IllegalArgumentException(key+" deve ser true ou false.");
        return value;
    }
    void validateCapacity(Domain d) {
        for(Domain.Group g:d.s.groups.values())if(g.bonusEarned>Math.min(Integer.MAX_VALUE,(long)g.members.size()*perMember))
            throw new IllegalArgumentException("A capacidade configurada retiraria bônus de ["+g.tag+"]. Configuração não aplicada; nenhum bônus ou claim foi removido.");
    }
}
