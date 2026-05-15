package com.codenzasoft.games.spring.messages;

import java.util.UUID;

public record KhMessage(UUID gameId, boolean quit) {}
