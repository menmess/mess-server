package by.mess.model

import java.util.concurrent.ThreadLocalRandom

typealias Id = Long

fun randomId(): Id = (ThreadLocalRandom.current().nextLong() % (2 shl 52))
