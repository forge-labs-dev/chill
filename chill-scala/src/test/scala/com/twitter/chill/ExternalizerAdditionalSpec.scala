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

import _root_.java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream}

class NonJavaSerializableClass(val value: Int)

/**
 * Additional tests for Externalizer focusing on edge cases and coverage gaps
 */
class ExternalizerAdditionalSpec extends AnyWordSpec with Matchers with BaseProperties {

  def getKryo: Kryo = KryoSerializer.registered.newKryo

  "Externalizer" should {
    "create via apply and get value" in {
      val ext = Externalizer(42)
      ext.get should be(42)
    }

    "return Some via getOption" in {
      val ext = Externalizer("hello")
      ext.getOption should be(Some("hello"))
    }

    "throw assertion error when set is called on already constructed Externalizer" in {
      val ext = Externalizer("first")
      an[AssertionError] should be thrownBy ext.set("second")
    }

    "report javaWorks correctly for Java serializable objects" in {
      val ext = Externalizer(List(1, 2, 3))
      ext.javaWorks should be(true)
    }

    "report javaWorks correctly for non-Java serializable objects" in {
      val ext = Externalizer(new NonJavaSerializableClass(42))
      ext.javaWorks should be(false)
    }

    "cache javaWorks result" in {
      val ext = Externalizer("test")
      val first = ext.javaWorks
      val second = ext.javaWorks
      first should be(second)
    }

    "handle roundtrip with Kryo for Java serializable objects" in {
      val ext = Externalizer(Map("a" -> 1, "b" -> 2))
      val rtExt = rt(ext)
      rtExt.get should be(Map("a" -> 1, "b" -> 2))
    }

    "handle roundtrip with Kryo for non-Java serializable objects" in {
      val ext = Externalizer(new NonJavaSerializableClass(100))
      val rtExt = rt(ext)
      rtExt.get.value should be(100)
    }

    "handle roundtrip with Java serialization" in {
      val ext = Externalizer(List("a", "b", "c"))
      val rtExt = jrt(ext)
      rtExt.get should be(List("a", "b", "c"))
    }

    "handle nested maps" in {
      val nested = Map("a" -> Map("inner" -> 1), "b" -> Map("inner" -> 2))
      val ext = Externalizer(nested)
      val rtExt = rt(ext)
      rtExt.get should be(nested)
    }

    "handle Option types" in {
      val extSome = Externalizer(Some("value"))
      val extNone: Externalizer[Option[String]] = Externalizer(None)

      rt(extSome).get should be(Some("value"))
      rt(extNone).get should be(None)
    }

    "handle Either types" in {
      val extLeft: Externalizer[Either[String, Int]] = Externalizer(Left("error"))
      val extRight: Externalizer[Either[String, Int]] = Externalizer(Right(42))

      rt(extLeft).get should be(Left("error"))
      rt(extRight).get should be(Right(42))
    }

    "handle tuples" in {
      val tuple = (1, "two", 3.0)
      val ext = Externalizer(tuple)
      rt(ext).get should be(tuple)
    }

    "work with writeExternal and readExternal" in {
      val ext = Externalizer(List(1, 2, 3))

      val baos = new ByteArrayOutputStream()
      val oos = new ObjectOutputStream(baos)
      ext.writeExternal(oos)
      oos.flush()
      oos.close()

      val bytes = baos.toByteArray

      val ext2 = new Externalizer[List[Int]]
      val bais = new ByteArrayInputStream(bytes)
      val ois = new ObjectInputStream(bais)
      ext2.readExternal(ois)

      ext2.get should be(List(1, 2, 3))
    }

    "work with write and read (KryoSerializable)" in {
      val ext = Externalizer("kryo test")
      val kryo = getKryo

      val output = new com.esotericsoftware.kryo.io.Output(1024)
      ext.write(kryo, output)
      output.flush()

      val bytes = output.toBytes
      val input = new com.esotericsoftware.kryo.io.Input(bytes)

      val ext2 = new Externalizer[String]
      ext2.read(kryo, input)

      ext2.get should be("kryo test")
    }

    "handle arrays" in {
      val ext = Externalizer(Array(1, 2, 3))
      val rtExt = rt(ext)
      rtExt.get should be(Array(1, 2, 3))
    }

    "handle empty collections" in {
      val extEmptyList = Externalizer(List.empty[Int])
      val extEmptyMap = Externalizer(Map.empty[String, Int])
      val extEmptySet = Externalizer(Set.empty[String])

      rt(extEmptyList).get should be(List.empty[Int])
      rt(extEmptyMap).get should be(Map.empty[String, Int])
      rt(extEmptySet).get should be(Set.empty[String])
    }

    "handle null values wrapped in Option" in {
      val ext: Externalizer[Option[String]] = Externalizer(Option(null))
      rt(ext).get should be(None)
    }

    "handle primitives" in {
      val extInt = Externalizer(42)
      val extLong = Externalizer(123L)
      val extDouble = Externalizer(3.14)
      val extBoolean = Externalizer(true)

      rt(extInt).get should be(42)
      rt(extLong).get should be(123L)
      rt(extDouble).get should be(3.14)
      rt(extBoolean).get should be(true)
    }

    "handle strings" in {
      val ext = Externalizer("hello world")
      rt(ext).get should be("hello world")
    }

    "handle BigInt and BigDecimal" in {
      val extBigInt = Externalizer(BigInt("12345678901234567890"))
      val extBigDecimal = Externalizer(BigDecimal("3.141592653589793238"))

      rt(extBigInt).get should be(BigInt("12345678901234567890"))
      rt(extBigDecimal).get should be(BigDecimal("3.141592653589793238"))
    }
  }

  "Externalizer with custom Kryo" should {
    "work with subclass providing custom kryo" in {
      class CustomExternalizer[T] extends Externalizer[T] {
        override protected def kryo: KryoInstantiator =
          new ScalaKryoInstantiator().setReferences(true)
      }

      val ext = new CustomExternalizer[List[Int]]
      ext.set(List(1, 2, 3))
      ext.get should be(List(1, 2, 3))

      val baos = new ByteArrayOutputStream()
      val oos = new ObjectOutputStream(baos)
      ext.writeExternal(oos)
      oos.flush()
      oos.close()

      val bytes = baos.toByteArray

      val ext2 = new CustomExternalizer[List[Int]]
      val bais = new ByteArrayInputStream(bytes)
      val ois = new ObjectInputStream(bais)
      ext2.readExternal(ois)

      ext2.get should be(List(1, 2, 3))
    }
  }
}
