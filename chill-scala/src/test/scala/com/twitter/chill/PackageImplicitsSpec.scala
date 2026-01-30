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

/**
 * Tests for implicit conversions in package.scala
 */
class PackageImplicitsSpec extends AnyWordSpec with Matchers with BaseProperties {

  "Package implicit conversions" should {
    "convert Kryo to RichKryo via toRich implicit" in {
      val kryo = new ScalaKryoInstantiator().newKryo
      // Using the implicit conversion
      val richKryo: RichKryo = kryo
      richKryo should not be null
      richKryo.k should be(kryo)
    }

    "convert Function0[Kryo] to KryoInstantiator via toInstantiator implicit" in {
      var callCount = 0
      val fn: () => Kryo = () => {
        callCount += 1
        new ScalaKryoInstantiator().newKryo
      }

      // Using the implicit conversion
      val instantiator: KryoInstantiator = fn
      instantiator should not be null

      // Verify the function is called when newKryo is invoked
      callCount should be(0)
      val kryo1 = instantiator.newKryo
      callCount should be(1)
      kryo1 should not be null

      // Each call should invoke the function
      val kryo2 = instantiator.newKryo
      callCount should be(2)
      kryo2 should not be null
    }

    "convert Function1[Kryo, Unit] to IKryoRegistrar via toRegistrar implicit" in {
      var registeredClass: Option[Class[_]] = None
      val fn: Kryo => Unit = { k =>
        k.register(classOf[String])
        registeredClass = Some(classOf[String])
      }

      // Using the implicit conversion
      val registrar: IKryoRegistrar = fn
      registrar should not be null

      // Verify the function is called when apply is invoked
      val kryo = new ScalaKryoInstantiator().newKryo
      registrar.apply(kryo)
      registeredClass should be(Some(classOf[String]))
    }

    "convert Iterable[IKryoRegistrar] to IKryoRegistrar via toRegistrar implicit" in {
      var count = 0
      val registrar1: IKryoRegistrar = new IKryoRegistrar {
        def apply(k: Kryo): Unit = count += 1
      }
      val registrar2: IKryoRegistrar = new IKryoRegistrar {
        def apply(k: Kryo): Unit = count += 10
      }
      val registrar3: IKryoRegistrar = new IKryoRegistrar {
        def apply(k: Kryo): Unit = count += 100
      }

      val items = List(registrar1, registrar2, registrar3)

      // Using the implicit conversion
      val combinedRegistrar: IKryoRegistrar = items
      combinedRegistrar should not be null

      // Verify all registrars are called
      val kryo = new ScalaKryoInstantiator().newKryo
      combinedRegistrar.apply(kryo)
      count should be(111)
    }

    "convert empty Iterable[IKryoRegistrar] to IKryoRegistrar" in {
      val items: List[IKryoRegistrar] = Nil
      val combinedRegistrar: IKryoRegistrar = items
      combinedRegistrar should not be null

      // Should not throw when applied to empty list
      val kryo = new ScalaKryoInstantiator().newKryo
      noException should be thrownBy combinedRegistrar.apply(kryo)
    }
  }

  "printIfRegistered" should {
    "print message when class is already registered" in {
      val registrar = printIfRegistered(classOf[String])
      val kryo = new ScalaKryoInstantiator().newKryo
      kryo.register(classOf[String])

      // This should print to stderr when class is registered
      // We can't easily capture stderr, but we verify it doesn't throw
      noException should be thrownBy registrar.apply(kryo)
    }

    "not print when class is not registered" in {
      val registrar = printIfRegistered(classOf[PackageImplicitsSpec])
      val kryo = new EmptyScalaKryoInstantiator().newKryo

      // This should not print since class is not registered
      noException should be thrownBy registrar.apply(kryo)
    }
  }

  "assertNotRegistered" should {
    "pass when class is not registered" in {
      val registrar = assertNotRegistered(classOf[PackageImplicitsSpec])
      val kryo = new EmptyScalaKryoInstantiator().newKryo

      noException should be thrownBy registrar.apply(kryo)
    }

    "throw AssertionError when class is already registered" in {
      val registrar = assertNotRegistered(classOf[String])
      val kryo = new ScalaKryoInstantiator().newKryo
      kryo.register(classOf[String])

      an[AssertionError] should be thrownBy registrar.apply(kryo)
    }
  }
}
