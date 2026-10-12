package com.codeeditor.domain.model

/** Thrown when a document exceeds [limitBytes] and is refused to protect memory. */
class FileTooLargeException(val limitBytes: Long) :
    Exception("Файл завеликий (максимум ${limitBytes / (1024 * 1024)} МБ)")
