/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.io

import io.ktor.utils.io.bits.*
import java.nio.ByteBuffer

internal actual object DefaultAllocator : Allocator {
    override fun alloc(size: Int): Memory = Memory.of(ByteBuffer.allocate(size))
    override fun free(instance: Memory) {}
}