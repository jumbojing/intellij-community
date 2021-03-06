// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage.indices

import com.intellij.util.containers.BidirectionalMap
import com.intellij.workspace.api.PersistentEntityId
import com.intellij.workspace.api.TypedEntity
import com.intellij.workspace.api.pstorage.PId

open class PersistentIdIndex private constructor(
  internal open val index: BidirectionalMap<PId<out TypedEntity>, PersistentEntityId<*>>
) {
  constructor() : this(BidirectionalMap<PId<out TypedEntity>, PersistentEntityId<*>>())

  internal fun getIdsByPersistentId(persistentId: PersistentEntityId<*>): List<PId<out TypedEntity>>? =
    index.getKeysByValue(persistentId)

  internal fun getPersistentId(id: PId<out TypedEntity>): PersistentEntityId<*>? = index[id]

  class MutablePersistentIdIndex private constructor(
    override var index: BidirectionalMap<PId<out TypedEntity>, PersistentEntityId<*>>
  ) : PersistentIdIndex(index) {

    private var freezed = true

    internal fun index(id: PId<out TypedEntity>, persistentId: PersistentEntityId<*>? = null) {
      startWrite()
      index.remove(id)
      if (persistentId == null) return
      index[id] = persistentId
    }

    private fun startWrite() {
      if (!freezed) return
      freezed = false
      index = copyIndex()
    }

    private fun copyIndex(): BidirectionalMap<PId<out TypedEntity>, PersistentEntityId<*>> {
      val copy = BidirectionalMap<PId<out TypedEntity>, PersistentEntityId<*>>()
      index.keys.forEach { key -> index[key]?.also { value -> copy[key] = value } }
      return copy
    }

    fun toImmutable(): PersistentIdIndex {
      freezed = true
      return PersistentIdIndex(index)
    }

    companion object {
      fun from(other: PersistentIdIndex): MutablePersistentIdIndex = MutablePersistentIdIndex(other.index)
    }
  }
}