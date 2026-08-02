package org.waveskimmer.plugins.utils

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.random.Random

class XOptionalTest : FunSpec({

    test("basic semantics") {

        val x = Some("Hello")
        x.value shouldBe "Hello"
        x.present shouldBe true

        Empty.present shouldBe false

        val flag = Random.nextBoolean()
        val y = when {
            flag -> Some(flag.toString())
            else -> Empty
        }
        when (y) {
            Some(x) -> x.value shouldBe "true"
            else -> y is Empty
        }
    }

    test("get works as designed") {

        Some("hello").get() shouldBe "hello"

        shouldThrow<NoSuchElementException> {
            Empty.get()
        }
    }
})
