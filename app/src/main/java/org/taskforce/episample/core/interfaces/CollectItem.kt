package org.taskforce.episample.core.interfaces

interface CollectItem: Breadcrumb {
    val id: String?
    val title: String?
    val note: String?
    val image: String?

    val displayDate: String
        get() = dateCreated.toString()
}