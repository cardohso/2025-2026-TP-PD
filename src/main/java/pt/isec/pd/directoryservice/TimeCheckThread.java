// java
package pt.isec.pd.directoryservice;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class TimeCheckThread implements Runnable {
    private final List<String> serverList;
    private final ConcurrentHashMap<String, Long> lastSeen;
    private final AtomicReference<String> principalRef;
    private static final long STALE_MS = 15_000; // consider server dead after 15s
    private static final long CHECK_INTERVAL_MS = 5_000;

    public TimeCheckThread(List<String> serverList, ConcurrentHashMap<String, Long> lastSeen, AtomicReference<String> principalRef) {
        this.serverList = serverList;
        this.lastSeen = lastSeen;
        this.principalRef = principalRef;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(CHECK_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            long now = System.currentTimeMillis();
            boolean principalChanged = false;
            String oldPrincipal = principalRef.get();

            synchronized (serverList) {
                Iterator<Map.Entry<String, Long>> it = lastSeen.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<String, Long> e = it.next();
                    if (now - e.getValue() > STALE_MS) {
                        String addr = e.getKey();
                        it.remove();
                        serverList.remove(addr);
                        System.out.println("[DS-TimeCheck] Removed stale server: " + addr);
                    }
                }

                // if current principal no longer present, choose a new one (first in list) or clear
                String curr = principalRef.get();
                if (curr != null && !serverList.contains(curr)) {
                    String newPrincipal = serverList.isEmpty() ? null : serverList.get(0);
                    principalRef.set(newPrincipal);
                    principalChanged = true;
                    System.out.println("[DS-TimeCheck] Principal changed from " + curr + " to " + newPrincipal);
                }
            }

            if (principalChanged) {
                String newPrincipal = principalRef.get();
                if (newPrincipal != null) {
                    // notify backups of the new principal
                    DirectoryService.notifyBackupsOfNewPrincipal(newPrincipal);
                } else {
                    System.out.println("[DS-TimeCheck] No principal available after cleanup.");
                }
            }
        }
    }
}
