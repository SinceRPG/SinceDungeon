package net.danh.sinceDungeon.utils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Small runtime scheduler bridge for Paper and Folia.
 * Paper uses the normal Bukkit scheduler, while Folia uses async, global, region,
 * and entity schedulers so world or entity work runs on the owning thread.
 */
public final class SchedulerCompat {

    private static final boolean FOLIA = hasMethod(Bukkit.getServer().getClass(), "getGlobalRegionScheduler");

    private SchedulerCompat() {
    }

    public static boolean isFolia() {
        return FOLIA;
    }

    public static TaskHandle runGlobal(Plugin plugin, Runnable task) {
        if (!FOLIA) {
            return TaskHandle.bukkit(Bukkit.getScheduler().runTask(plugin, task));
        }
        return invokeScheduler(Bukkit.getServer(), "getGlobalRegionScheduler", "run", plugin, task);
    }

    public static TaskHandle runGlobalLater(Plugin plugin, Runnable task, long delayTicks) {
        if (!FOLIA) {
            return TaskHandle.bukkit(Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks));
        }
        return invokeScheduler(Bukkit.getServer(), "getGlobalRegionScheduler", "runDelayed", plugin, task, delayTicks);
    }

    public static TaskHandle runGlobalTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        if (!FOLIA) {
            return TaskHandle.bukkit(Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks));
        }
        return invokeScheduler(Bukkit.getServer(), "getGlobalRegionScheduler", "runAtFixedRate", plugin, task, delayTicks, periodTicks);
    }

    public static TaskHandle runAsync(Plugin plugin, Runnable task) {
        if (!FOLIA) {
            return TaskHandle.bukkit(Bukkit.getScheduler().runTaskAsynchronously(plugin, task));
        }
        return invokeScheduler(Bukkit.getServer(), "getAsyncScheduler", "runNow", plugin, task);
    }

    public static TaskHandle runAsyncLater(Plugin plugin, Runnable task, long delayTicks) {
        if (!FOLIA) {
            return TaskHandle.bukkit(Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delayTicks));
        }
        return invokeAsyncScheduler("runDelayed", plugin, task, ticksToMillis(delayTicks));
    }

    public static TaskHandle runAsyncTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        if (!FOLIA) {
            return TaskHandle.bukkit(Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks));
        }
        return invokeAsyncScheduler("runAtFixedRate", plugin, task, ticksToMillis(delayTicks), ticksToMillis(periodTicks));
    }

    public static TaskHandle runAtLocation(Plugin plugin, Location location, Runnable task) {
        if (!FOLIA || location == null || location.getWorld() == null) {
            return runGlobal(plugin, task);
        }
        try {
            Object scheduler = Bukkit.getServer().getClass().getMethod("getRegionScheduler").invoke(Bukkit.getServer());
            Method run = findMethod(scheduler.getClass(), "run", 3);
            Object scheduled = run.invoke(scheduler, plugin, location, toConsumer(task));
            return TaskHandle.reflective(scheduled);
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("Failed to use Folia region scheduler, falling back to global scheduler: " + e.getMessage());
            return runGlobal(plugin, task);
        }
    }

    public static TaskHandle runAtLocationLater(Plugin plugin, Location location, Runnable task, long delayTicks) {
        if (!FOLIA || location == null || location.getWorld() == null) {
            return runGlobalLater(plugin, task, delayTicks);
        }
        try {
            Object scheduler = Bukkit.getServer().getClass().getMethod("getRegionScheduler").invoke(Bukkit.getServer());
            Method run = findMethod(scheduler.getClass(), "runDelayed", 4);
            Object scheduled = run.invoke(scheduler, plugin, location, toConsumer(task), delayTicks);
            return TaskHandle.reflective(scheduled);
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("Failed to use Folia delayed region scheduler, falling back to global scheduler: " + e.getMessage());
            return runGlobalLater(plugin, task, delayTicks);
        }
    }

    public static TaskHandle runAtLocationTimer(Plugin plugin, Location location, Runnable task, long delayTicks, long periodTicks) {
        if (!FOLIA || location == null || location.getWorld() == null) {
            return runGlobalTimer(plugin, task, delayTicks, periodTicks);
        }
        try {
            Object scheduler = Bukkit.getServer().getClass().getMethod("getRegionScheduler").invoke(Bukkit.getServer());
            Method run = findMethod(scheduler.getClass(), "runAtFixedRate", 5);
            Object scheduled = run.invoke(scheduler, plugin, location, toConsumer(task), delayTicks, periodTicks);
            return TaskHandle.reflective(scheduled);
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("Failed to use Folia repeating region scheduler, falling back to global scheduler: " + e.getMessage());
            return runGlobalTimer(plugin, task, delayTicks, periodTicks);
        }
    }

    public static TaskHandle runAtEntity(Plugin plugin, Entity entity, Runnable task) {
        if (!FOLIA || entity == null) {
            return runGlobal(plugin, task);
        }
        try {
            Object scheduler = entity.getClass().getMethod("getScheduler").invoke(entity);
            Method run = findMethod(scheduler.getClass(), "run", 3);
            Object scheduled = run.invoke(scheduler, plugin, toConsumer(task), null);
            return TaskHandle.reflective(scheduled);
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("Failed to use Folia entity scheduler, falling back to global scheduler: " + e.getMessage());
            return runGlobal(plugin, task);
        }
    }

    public static TaskHandle runAtEntityTimer(Plugin plugin, Entity entity, Runnable task, long delayTicks, long periodTicks) {
        if (!FOLIA || entity == null) {
            return runGlobalTimer(plugin, task, delayTicks, periodTicks);
        }
        try {
            Object scheduler = entity.getClass().getMethod("getScheduler").invoke(entity);
            Method run = findMethod(scheduler.getClass(), "runAtFixedRate", 5);
            Object scheduled = run.invoke(scheduler, plugin, toConsumer(task), null, delayTicks, periodTicks);
            return TaskHandle.reflective(scheduled);
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("Failed to use Folia entity timer, falling back to global scheduler: " + e.getMessage());
            return runGlobalTimer(plugin, task, delayTicks, periodTicks);
        }
    }

    private static TaskHandle invokeScheduler(Object owner, String schedulerMethod, String method, Plugin plugin, Runnable task, long... ticksOrMillis) {
        try {
            Object scheduler = owner.getClass().getMethod(schedulerMethod).invoke(owner);
            int parameterCount = 2 + ticksOrMillis.length;
            Method schedulerRun = findMethod(scheduler.getClass(), method, parameterCount);
            Object[] args = new Object[parameterCount];
            args[0] = plugin;
            args[1] = toConsumer(task);
            for (int i = 0; i < ticksOrMillis.length; i++) {
                args[i + 2] = ticksOrMillis[i];
            }
            return TaskHandle.reflective(schedulerRun.invoke(scheduler, args));
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("Failed to use Folia scheduler, running task on Paper scheduler fallback: " + e.getMessage());
            return TaskHandle.bukkit(Bukkit.getScheduler().runTask(plugin, task));
        }
    }

    private static TaskHandle invokeAsyncScheduler(String method, Plugin plugin, Runnable task, long... millis) {
        try {
            Object scheduler = Bukkit.getServer().getClass().getMethod("getAsyncScheduler").invoke(Bukkit.getServer());
            int parameterCount = 3 + millis.length;
            Method schedulerRun = findMethod(scheduler.getClass(), method, parameterCount);
            Object[] args = new Object[parameterCount];
            args[0] = plugin;
            args[1] = toConsumer(task);
            for (int i = 0; i < millis.length; i++) {
                args[i + 2] = millis[i];
            }
            args[parameterCount - 1] = TimeUnit.MILLISECONDS;
            return TaskHandle.reflective(schedulerRun.invoke(scheduler, args));
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("Failed to use Folia async scheduler, running task on Paper scheduler fallback: " + e.getMessage());
            return TaskHandle.bukkit(Bukkit.getScheduler().runTaskAsynchronously(plugin, task));
        }
    }

    private static Consumer<Object> toConsumer(Runnable task) {
        return ignored -> task.run();
    }

    private static Method findMethod(Class<?> type, String name, int parameterCount) throws NoSuchMethodException {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == parameterCount) {
                return method;
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + name + "/" + parameterCount);
    }

    private static boolean hasMethod(Class<?> type, String name) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static long ticksToMillis(long ticks) {
        return Math.max(1L, ticks * 50L);
    }

    public static final class TaskHandle {
        private final Object task;

        private TaskHandle(Object task) {
            this.task = task;
        }

        private static TaskHandle bukkit(BukkitTask task) {
            return new TaskHandle(task);
        }

        private static TaskHandle reflective(Object task) {
            return new TaskHandle(task);
        }

        public void cancel() {
            if (task == null) return;
            if (task instanceof BukkitTask bukkitTask) {
                bukkitTask.cancel();
                return;
            }
            try {
                task.getClass().getMethod("cancel").invoke(task);
            } catch (ReflectiveOperationException ignored) {
            }
        }

        public boolean isCancelled() {
            if (task == null) return true;
            if (task instanceof BukkitTask bukkitTask) {
                return bukkitTask.isCancelled();
            }
            try {
                Object value = task.getClass().getMethod("isCancelled").invoke(task);
                return value instanceof Boolean cancelled && cancelled;
            } catch (ReflectiveOperationException ignored) {
                return false;
            }
        }
    }
}
