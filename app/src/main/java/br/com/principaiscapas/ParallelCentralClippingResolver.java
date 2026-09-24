package br.com.principaiscapas;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * v0.7.7.7 — acelera o Gmail sem cortar candidatas.
 *
 * Em vez de colocar todas as páginas de todos os jornais em uma única fila,
 * cria uma fila por jornal e processa até 4 jornais ao mesmo tempo. Dentro de
 * cada jornal a ordem original das candidatas continua intacta, porque cada
 * grupo usa o CentralClippingWebResolver sequencial já aprovado.
 */
public class ParallelCentralClippingResolver {
    public interface Callback {
        void onComplete(Map<String, List<String>> coverUrls, List<String> errors);
    }

    private static final int MAX_PARALLEL_NEWSPAPERS = 4;

    private final Activity activity;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<JournalJob> journalJobs = new ArrayList<>();
    private final List<CentralClippingWebResolver> activeResolvers = new ArrayList<>();
    private final Map<String, List<String>> resolved = new LinkedHashMap<>();
    private final List<String> errors = new ArrayList<>();

    private Callback callback;
    private int nextIndex = 0;
    private int running = 0;
    private boolean finished = false;

    private static class JournalJob {
        final String name;
        final List<String> urls;

        JournalJob(String name, List<String> urls) {
            this.name = name;
            this.urls = urls;
        }
    }

    public ParallelCentralClippingResolver(Activity activity) {
        this.activity = activity;
    }

    public void resolve(Map<String, List<String>> matterUrls, Callback callback) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(() -> resolve(matterUrls, callback));
            return;
        }

        this.callback = callback;
        journalJobs.clear();
        activeResolvers.clear();
        resolved.clear();
        errors.clear();
        nextIndex = 0;
        running = 0;
        finished = false;

        if (matterUrls != null) {
            for (Map.Entry<String, List<String>> entry : matterUrls.entrySet()) {
                if (isWebOnly(entry.getKey()) || entry.getValue() == null || entry.getValue().isEmpty()) continue;
                List<String> clean = new ArrayList<>();
                for (String url : entry.getValue()) {
                    if (url != null && !url.trim().isEmpty()) clean.add(url.trim());
                    else clean.add("");
                }
                if (!clean.isEmpty()) journalJobs.add(new JournalJob(entry.getKey(), clean));
            }
        }

        if (journalJobs.isEmpty()) {
            finish();
            return;
        }
        launchMore();
    }

    private void launchMore() {
        if (finished) return;

        while (running < MAX_PARALLEL_NEWSPAPERS && nextIndex < journalJobs.size()) {
            JournalJob job = journalJobs.get(nextIndex++);
            startJournal(job);
        }

        if (running == 0 && nextIndex >= journalJobs.size()) finish();
    }

    private void startJournal(JournalJob job) {
        running++;

        CentralClippingWebResolver resolver = new CentralClippingWebResolver(activity);
        activeResolvers.add(resolver);

        Map<String, List<String>> oneJournal = new LinkedHashMap<>();
        oneJournal.put(job.name, new ArrayList<>(job.urls));

        resolver.resolve(oneJournal, (covers, resolverErrors) -> {
            List<String> list = covers == null ? null : covers.get(job.name);
            if (list != null) resolved.put(job.name, new ArrayList<>(list));
            if (resolverErrors != null && !resolverErrors.isEmpty()) errors.addAll(resolverErrors);

            activeResolvers.remove(resolver);
            running--;
            launchMore();
        });
    }

    private void finish() {
        if (finished) return;
        finished = true;

        Map<String, List<String>> ordered = new LinkedHashMap<>();
        for (JournalJob job : journalJobs) {
            List<String> list = resolved.get(job.name);
            if (list != null) ordered.put(job.name, new ArrayList<>(list));
        }

        if (callback != null) callback.onComplete(ordered, new ArrayList<>(errors));
    }

    private static boolean isWebOnly(String name) {
        return "VALOR ECONÔMICO".equalsIgnoreCase(name) ||
                "THE WASHINGTON POST".equalsIgnoreCase(name);
    }
}
