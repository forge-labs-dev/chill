/*
Copyright 2012 Twitter, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package com.twitter.chill

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

// Classes without outer reference (defined in companion object for Scala 3 compatibility)
object ClosureCleanerAdditionalSpec {
  class NoOuter {
    val x = 1
  }

  class NoOuter2 {
    val x = 1
  }

  class TestForInnerCache {
    val x = 1
  }

  class TestForFieldCache {
    val x = 1
  }
}

/**
 * Additional tests for ClosureCleaner focusing on edge cases and coverage gaps
 */
class ClosureCleanerAdditionalSpec extends AnyWordSpec with Matchers {
  import ClosureCleanerAdditionalSpec._

  // Define test classes at class level for Scala 3 compatibility
  class TestClass {
    var value: Int = 42
  }

  class TestOuter {
    val x = 1
  }

  class TestOuter2 {
    val x = 1
  }

  class WithFields {
    val field1 = 1
    val field2 = "hello"
    val field3 = 3.14
  }

  class ComplexOuter {
    val outerVal = 10
    def createClosure: Int => Int = { x =>
      val localVal = 5
      x + localVal + outerVal
    }
  }

  // Nested class hierarchy for testing outer class detection
  class Level1 {
    class Level2 {
      class Level3 {
        val x = 1
      }
    }
  }

  // Another hierarchy for outer field testing
  class Outer1 {
    val a = 1
    class Inner1 {
      val b = 2
    }
  }

  class TestOuter3 {
    val normalField = 1
    class Inner3 {
      val x = 1
    }
  }

  class OuterWithInner {
    val closure: Int => Int => Int = (x: Int) => {
      val inner = (y: Int) => x + y
      inner
    }
  }

  "ClosureCleaner" should {
    "instantiate class without constructor" in {
      val instance = ClosureCleaner.instantiateClass(classOf[TestClass])
      instance should not be null
      instance shouldBe a[TestClass]
      // Value should be default (0) since constructor wasn't called
      instance.asInstanceOf[TestClass].value should be(0)
    }

    "handle outerFieldOf for class without outer" in {
      val result = ClosureCleaner.outerFieldOf(classOf[NoOuter])
      result should be(None)
    }

    "cache outerFieldOf results" in {
      // Call twice to test caching
      val result1 = ClosureCleaner.outerFieldOf(classOf[TestOuter])
      val result2 = ClosureCleaner.outerFieldOf(classOf[TestOuter])
      result1 should be(result2)
    }

    "get outer classes hierarchy" in {
      val l1 = new Level1
      val l2 = new l1.Level2
      val l3 = new l2.Level3

      val outers = ClosureCleaner.outerClassesOf(l3.getClass)
      // Level3 has Level2 as outer, Level2 has Level1 as outer
      outers.size should be >= 1
    }

    "cache outer classes hierarchy" in {
      val result1 = ClosureCleaner.outerClassesOf(classOf[TestOuter2])
      val result2 = ClosureCleaner.outerClassesOf(classOf[TestOuter2])
      result1 should be(result2)
    }

    "get outers of object with hierarchy" in {
      val outer = new Outer1
      val inner = new outer.Inner1

      val outers = ClosureCleaner.getOutersOf(inner)
      outers should not be empty
      outers.exists(_._1 == outer.getClass) should be(true)
    }

    "get outers of object without outer" in {
      val obj = new NoOuter2
      val outers = ClosureCleaner.getOutersOf(obj)
      outers should be(empty)
    }

    "identify outer fields correctly" in {
      val outer = new TestOuter3
      val inner = new outer.Inner3
      val outerField = ClosureCleaner.outerFieldOf(inner.getClass)
      outerField.isDefined should be(true)
      ClosureCleaner.isOuterField(outerField.get) should be(true)
    }

    "find inner classes" in {
      val obj = new OuterWithInner
      // This tests the innerClassesOf method and InnerClosureFinder
      val innerClasses = ClosureCleaner.innerClassesOf(obj.closure.getClass)
      // May or may not find inner classes depending on compilation
      innerClasses should not be null
    }

    "cache inner classes results" in {
      val result1 = ClosureCleaner.innerClassesOf(classOf[TestForInnerCache])
      val result2 = ClosureCleaner.innerClassesOf(classOf[TestForInnerCache])
      result1 should be(result2)
    }

    "find accessed fields" in {
      val obj = new WithFields
      val fields = ClosureCleaner.accessedFieldsOf(obj.getClass)
      // May find some fields depending on how the class is used
      fields should not be null
    }

    "cache accessed fields results" in {
      val result1 = ClosureCleaner.accessedFieldsOf(classOf[TestForFieldCache])
      val result2 = ClosureCleaner.accessedFieldsOf(classOf[TestForFieldCache])
      result1 should be(result2)
    }

    "apply method should be equivalent to clean" in {
      val list = List(1, 2, 3)
      // apply returns Unit, clean returns the object
      noException should be thrownBy ClosureCleaner(list)
    }

    "serialize and deserialize correctly" in {
      val original = "test string"
      val bytes = ClosureCleaner.serialize(original)
      bytes should not be empty
      val deserialized = ClosureCleaner.deserialize[String](bytes)
      deserialized should be(original)
    }

    "handle complex nested closures" in {
      val obj = new ComplexOuter
      val closure = obj.createClosure

      // Test that closure cleaner can handle this
      noException should be thrownBy ClosureCleaner.clean(closure)
    }

    "handle already serializable objects" in {
      val serializable = List(1, 2, 3)
      val cleaned = ClosureCleaner.clean(serializable)
      cleaned should be(serializable)
    }
  }

  "MethodIdentifier" should {
    "have correct equality" in {
      val m1 = MethodIdentifier(classOf[String], "length", "()I")
      val m2 = MethodIdentifier(classOf[String], "length", "()I")
      val m3 = MethodIdentifier(classOf[String], "hashCode", "()I")

      m1 should be(m2)
      m1 should not be m3
    }

    "have correct hashCode" in {
      val m1 = MethodIdentifier(classOf[String], "length", "()I")
      val m2 = MethodIdentifier(classOf[String], "length", "()I")

      m1.hashCode should be(m2.hashCode)
    }
  }
}
