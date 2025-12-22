package com.yourorg.coinflip.util;

import com.yourorg.coinflip.CoinFlipPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public final class GeyserUtil {

    private final CoinFlipPlugin plugin;
    private boolean available;
    private Object floodgateApi;
    private Method isFloodgatePlayer;
    private Method sendForm;
    private boolean sendFormUsesPlayer;
    private Method simpleFormBuilder;
    private Method modalFormBuilder;

    public GeyserUtil(CoinFlipPlugin plugin) {
        this.plugin = plugin;
        init();
    }

    public boolean isBedrockPlayer(Player player) {
        if (!available || player == null) {
            return false;
        }
        try {
            return (boolean) isFloodgatePlayer.invoke(floodgateApi, player.getUniqueId());
        } catch (Exception ignored) {
            return false;
        }
    }

    public boolean sendSimpleForm(UUID playerId, String title, String content, List<String> buttons, IntConsumer onSelect) {
        if (!available || playerId == null) {
            return false;
        }
        Object form = buildSimpleForm(title, content, buttons, onSelect);
        if (form == null) {
            return false;
        }
        return sendForm(playerId, form);
    }

    public boolean sendModalForm(UUID playerId, String title, String content, String button1, String button2, IntConsumer onSelect) {
        if (!available || playerId == null) {
            return false;
        }
        Object form = buildModalForm(title, content, button1, button2, onSelect);
        if (form == null) {
            return false;
        }
        return sendForm(playerId, form);
    }

    private void init() {
        try {
            Class<?> floodgateApiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Method getInstance = floodgateApiClass.getMethod("getInstance");
            Object api = getInstance.invoke(null);

            Method isPlayer = floodgateApiClass.getMethod("isFloodgatePlayer", UUID.class);
            Method send = findSendForm(floodgateApiClass);
            Method simpleBuilder = Class.forName("org.geysermc.cumulus.form.SimpleForm").getMethod("builder");
            Method modalBuilder = Class.forName("org.geysermc.cumulus.form.ModalForm").getMethod("builder");

            if (api != null && isPlayer != null && send != null && simpleBuilder != null && modalBuilder != null) {
                this.available = true;
                this.floodgateApi = api;
                this.isFloodgatePlayer = isPlayer;
                this.sendForm = send;
                this.simpleFormBuilder = simpleBuilder;
                this.modalFormBuilder = modalBuilder;
            }
        } catch (Exception ignored) {
            this.available = false;
        }
    }

    private Method findSendForm(Class<?> floodgateApiClass) {
        for (Method method : floodgateApiClass.getMethods()) {
            if (!"sendForm".equals(method.getName()) || method.getParameterCount() != 2) {
                continue;
            }
            Class<?> firstParam = method.getParameterTypes()[0];
            if (UUID.class.isAssignableFrom(firstParam)) {
                this.sendFormUsesPlayer = false;
                return method;
            }
            if (Player.class.isAssignableFrom(firstParam)) {
                this.sendFormUsesPlayer = true;
                return method;
            }
        }
        return null;
    }

    private boolean sendForm(UUID playerId, Object form) {
        try {
            if (sendFormUsesPlayer) {
                Player player = Bukkit.getPlayer(playerId);
                if (player == null) {
                    return false;
                }
                sendForm.invoke(floodgateApi, player, form);
            } else {
                sendForm.invoke(floodgateApi, playerId, form);
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private Object buildSimpleForm(String title, String content, List<String> buttons, IntConsumer onSelect) {
        try {
            Object builder = simpleFormBuilder.invoke(null);
            applyString(builder, "title", title);
            applyString(builder, "content", content);
            if (buttons != null) {
                for (String button : buttons) {
                    applyString(builder, "button", button);
                }
            }
            if (onSelect != null) {
                attachHandler(builder, response -> {
                    int index = resolveButtonId(response);
                    if (index < 0 && buttons != null) {
                        String text = resolveButtonText(response);
                        if (text != null) {
                            index = buttons.indexOf(text);
                        }
                    }
                    if (index >= 0) {
                        int selectedIndex = index;
                        runSync(() -> onSelect.accept(selectedIndex));
                    }
                });
            }
            return invokeNoArgs(builder, "build");
        } catch (Exception ignored) {
            return null;
        }
    }

    private Object buildModalForm(String title, String content, String button1, String button2, IntConsumer onSelect) {
        try {
            Object builder = modalFormBuilder.invoke(null);
            applyString(builder, "title", title);
            applyString(builder, "content", content);
            applyString(builder, "button1", button1);
            applyString(builder, "button2", button2);
            if (onSelect != null) {
                List<String> buttons = List.of(button1, button2);
                attachHandler(builder, response -> {
                    int index = resolveButtonId(response);
                    if (index < 0) {
                        String text = resolveButtonText(response);
                        if (text != null) {
                            index = buttons.indexOf(text);
                        }
                    }
                    if (index >= 0) {
                        int selectedIndex = index;
                        runSync(() -> onSelect.accept(selectedIndex));
                    }
                });
            }
            return invokeNoArgs(builder, "build");
        } catch (Exception ignored) {
            return null;
        }
    }

    private void applyString(Object builder, String methodName, String value) throws Exception {
        if (value == null) {
            return;
        }
        Method method = findStringMethod(builder.getClass(), methodName);
        if (method != null) {
            method.invoke(builder, value);
        }
    }

    private Method findStringMethod(Class<?> type, String methodName) {
        for (Method method : type.getMethods()) {
            if (!methodName.equals(method.getName()) || method.getParameterCount() != 1) {
                continue;
            }
            Class<?> param = method.getParameterTypes()[0];
            if (param.isAssignableFrom(String.class) || CharSequence.class.isAssignableFrom(param)) {
                return method;
            }
        }
        return null;
    }

    private void attachHandler(Object builder, Consumer<Object> handler) throws Exception {
        Method method = findHandlerMethod(builder.getClass(), handler);
        if (method != null) {
            method.invoke(builder, handler);
        }
    }

    private Method findHandlerMethod(Class<?> type, Consumer<Object> handler) {
        String[] names = {"validResultHandler", "responseHandler", "resultHandler"};
        for (String name : names) {
            for (Method method : type.getMethods()) {
                if (!name.equals(method.getName()) || method.getParameterCount() != 1) {
                    continue;
                }
                Class<?> param = method.getParameterTypes()[0];
                if (param.isAssignableFrom(handler.getClass()) || param.isAssignableFrom(Consumer.class)) {
                    return method;
                }
            }
        }
        return null;
    }

    private Object invokeNoArgs(Object target, String methodName) throws Exception {
        Method method = target.getClass().getMethod(methodName);
        return method.invoke(target);
    }

    private int resolveButtonId(Object response) {
        if (response == null) {
            return -1;
        }
        try {
            Method method = response.getClass().getMethod("clickedButtonId");
            Object id = method.invoke(response);
            if (id instanceof Number number) {
                return number.intValue();
            }
            if (id instanceof Boolean bool) {
                return bool ? 0 : 1;
            }
        } catch (Exception ignored) {
            return -1;
        }
        return -1;
    }

    private String resolveButtonText(Object response) {
        if (response == null) {
            return null;
        }
        try {
            Method method = response.getClass().getMethod("clickedButtonText");
            Object text = method.invoke(response);
            return text != null ? text.toString() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void runSync(Runnable action) {
        if (Bukkit.isPrimaryThread()) {
            action.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, action);
        }
    }
}
