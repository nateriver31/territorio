package dev.nateriver.territorio;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Separate from state persistence: bounded, ordered asynchronous daily files. */
final class ActivityLog implements AutoCloseable {
    private static final ZoneId ZONE=ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter TIME=DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZONE);
    private final Path folder;
    private final Clock clock;
    private final Consumer<String> warning;
    private final ThreadPoolExecutor writer;
    private volatile int retention;
    private volatile boolean failed;
    private LocalDate rotated;

    ActivityLog(Path pluginFolder,int retention,Consumer<String> warning) {this(pluginFolder,retention,warning,Clock.systemUTC());}
    ActivityLog(Path pluginFolder,int retention,Consumer<String> warning,Clock clock) {
        this.folder=pluginFolder.resolve("logs");this.retention=retention;this.warning=warning;this.clock=clock;
        writer=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(4096),r->{Thread t=new Thread(r,"Territorio-logs");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    }
    void retention(int days) {retention=days;}
    void record(String category,String text) {
        Instant time=clock.instant();String line="["+TIME.format(time)+"] ["+safe(category)+"] "+safe(text)+System.lineSeparator();
        try {writer.execute(()->{
            try {
                Files.createDirectories(folder);LocalDate date=time.atZone(ZONE).toLocalDate();
                if(!date.equals(rotated)) {rotate(date);rotated=date;}
                Files.writeString(folder.resolve("territorio-"+date+".log"),line,StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
            } catch(IOException|RuntimeException e) {failed=true;warning.accept("Falha ao gravar log diário do Território ("+e.getClass().getSimpleName()+"). Dados territoriais não foram apagados.");}
        });}catch(RejectedExecutionException e){failed=true;warning.accept("Fila de logs do Território indisponível ou cheia; registro não gravado.");}
    }
    private void rotate(LocalDate today) throws IOException {
        LocalDate oldest=today.minusDays(retention-1L);
        try(var paths=Files.newDirectoryStream(folder,"territorio-????-??-??.log")) {
            for(Path file:paths) {
                String name=file.getFileName().toString();
                if(!name.matches("territorio-\\d{4}-\\d{2}-\\d{2}\\.log")||Files.isSymbolicLink(file)||!Files.isRegularFile(file))continue;
                try {if(LocalDate.parse(name.substring(11,21)).isBefore(oldest))Files.delete(file);}
                catch(java.time.format.DateTimeParseException ignored) { /* Do not touch unrecognized files. */ }
            }
        }
    }
    private static String safe(String value) {return value==null?"":value.replaceAll("[\\r\\n\\p{Cntrl}]"," ");}
    boolean failed() {return failed;}
    void flush() throws Exception {writer.submit(()->{}).get(10,TimeUnit.SECONDS);}
    @Override public void close() throws Exception {
        writer.shutdown();if(!writer.awaitTermination(10,TimeUnit.SECONDS))throw new IOException("Tempo excedido ao concluir logs do Território.");
    }
}
