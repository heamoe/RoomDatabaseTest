package com.amos.roomdatabasepractice

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ContactViewModel(
    private val dao: ContactDao
) : ViewModel() {
    private val _sortType = MutableStateFlow(SortType.PHONE_NUMBER)
    private val _contact = _sortType
        .flatMapLatest { sortType ->
            when (sortType) {
                SortType.FIRST_NAME -> {
                    Log.d("ContactViewModel", "Fetching contacts by first name")
                    dao.getContactsOrderedByFirstName()
                }

                SortType.LAST_NAME -> {
                    Log.d("ContactViewModel", "Fetching contacts by last name")
                    dao.getContactsOrderedByLastName()
                }

                SortType.PHONE_NUMBER -> {
                    Log.d("ContactViewModel", "Fetching contacts by phone number")
                    dao.getContactsOrderedByPhoneNumber()
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())


    private val _state = MutableStateFlow(ContactState())
    val state = combine(_state, _sortType, _contact) { state, sortType, contacts ->
        state.copy(
            contacts = contacts,
            sortType = sortType
        )

    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ContactState())

    fun onEvent(event: ContactEvent) {
        when (event) {
            is ContactEvent.DeleteContact -> {
                viewModelScope.launch { dao.deleteContact(event.contact) }
            }

            ContactEvent.HideDialog -> {
                _state.update {
                    it.copy(
                        isAddingContact = false
                    )
                }
            }

            ContactEvent.SaveContact -> {
                val firstName = state.value.firstname
                val lastName = state.value.lastname
                val phoneNumber = state.value.phoneNumber
                if (firstName.isBlank() || lastName.isBlank() || phoneNumber.isBlank()) {
                    return
                }
                val contact = Contact(
                    firstname = firstName,
                    lastname = lastName,
                    phoneNumber = phoneNumber
                )
                viewModelScope.launch { dao.upsertContact(contact) }
                _state.update {
                    it.copy(
                        isAddingContact = false,
                        firstname = "",
                        lastname = "",

                        )
                }
            }

            is ContactEvent.SetFirstName -> {
                _state.update {
                    it.copy(
                        firstname = event.firstName
                    )
                }
            }

            is ContactEvent.SetLastName -> {
                _state.update {
                    it.copy(
                        lastname = event.lastName
                    )
                }
            }

            is ContactEvent.SetPhoneNumber -> {
                _state.update {
                    it.copy(
                        phoneNumber = event.phoneNumber
                    )
                }
            }

            ContactEvent.ShowDialog -> {
                _state.update {
                    it.copy(
                        isAddingContact = true
                    )
                }
            }

            is ContactEvent.SortContacts -> {
                Log.d("ContactViewModel", "Updating sort type to: ${event.sortType}")
                _sortType.value = event.sortType
            }
        }
    }
}