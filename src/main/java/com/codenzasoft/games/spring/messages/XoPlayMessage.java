package com.codenzasoft.games.spring.messages;

/** A STOMP message from a client requesting a board position be played. */
public record XoPlayMessage(int row, int column) {}
