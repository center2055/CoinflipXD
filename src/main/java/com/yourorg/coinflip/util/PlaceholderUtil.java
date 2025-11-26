package com.yourorg.coinflip.util;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public final class PlaceholderUtil {

    private PlaceholderUtil() {
    }

    public static TagResolver merge(TagResolver... resolvers) {
        if (resolvers == null || resolvers.length == 0) {
            return TagResolver.empty();
        }
        TagResolver.Builder builder = TagResolver.builder();
        for (TagResolver resolver : resolvers) {
            if (resolver != null) {
                builder.resolver(resolver);
            }
        }
        return builder.build();
    }
}

