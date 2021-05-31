package by.mess.model

import java.lang.Math.abs
import java.util.concurrent.ThreadLocalRandom

typealias Id = Long

fun randomId(): Id = abs(ThreadLocalRandom.current().nextLong() % (2 shl 52))
