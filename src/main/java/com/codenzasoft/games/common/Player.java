package com.codenzasoft.games.common;

import java.util.UUID;

/** A player in a {@link Game}. */
public record Player(UUID id, String name) {}
