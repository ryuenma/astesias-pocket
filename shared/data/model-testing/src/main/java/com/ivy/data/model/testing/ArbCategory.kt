package com.ivy.data.model.testing

import arrow.core.None
import arrow.core.Option
import arrow.core.getOrElse
import com.ivy.data.model.Category
import com.ivy.data.model.CategoryId
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.removeEdgecases
import io.kotest.property.arbitrary.uuid

fun Arb.Companion.category(
    categoryId: Option<CategoryId> = None,
): Arb<Category> = arbitrary {
    Category(
        id = categoryId.getOrElse { Arb.categoryId().bind() },
        name = Arb.notBlankTrimmedString().bind(),
        color = Arb.colorInt().bind(),
        icon = Arb.maybe(Arb.iconAsset()).bind(),
        orderNum = Arb.double().removeEdgecases().bind(),
        // An arbitrary parent: these generators feed flat-list properties, not tree
        // invariants, so a genuine tree is something a test should build explicitly
        // rather than expect from here.
        parentId = Arb.maybe(Arb.categoryId()).bind(),
    )
}

fun Arb.Companion.categoryId(): Arb<CategoryId> = Arb.uuid().map(::CategoryId)