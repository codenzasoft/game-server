package com.codenzasoft.games.spring.services.kh;

import com.codenzasoft.games.kingshand.CardTarget;
import com.codenzasoft.games.kingshand.OpponentResourceTarget;
import com.codenzasoft.games.kingshand.ResourceTarget;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Persistence mix-in adding polymorphic type information to the sealed {@code EffectTarget}
 * interface so it round-trips. Serialization to the client never needs this (the wire is
 * read-only), so the type tag is added <em>only</em> on the persistence mapper — the client JSON
 * shape is untouched. {@code WRAPPER_OBJECT} handles both the {@link ResourceTarget} enum (a
 * scalar) and the record targets uniformly, e.g. {@code {"resource":"POPULATION"}} or {@code
 * {"opponent":{...}}}.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.WRAPPER_OBJECT)
@JsonSubTypes({
  @JsonSubTypes.Type(value = ResourceTarget.class, name = "resource"),
  @JsonSubTypes.Type(value = CardTarget.class, name = "card"),
  @JsonSubTypes.Type(value = OpponentResourceTarget.class, name = "opponent")
})
interface EffectTargetPersistenceMixin {}
