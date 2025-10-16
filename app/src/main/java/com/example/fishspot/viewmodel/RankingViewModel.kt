package com.example.fishspot.viewmodel

import androidx.lifecycle.ViewModel
import com.example.fishspot.data.Korisnici
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class RankingViewModel : ViewModel() {
    private val firestore = FirebaseFirestore.getInstance()
    private val _userRanking = MutableStateFlow<List<Korisnici>>(emptyList())
    val userRanking = _userRanking.asStateFlow()

    init {
        fetchUserRanking()
    }

    private fun fetchUserRanking() {
        firestore.collection("Korisnici")
            .orderBy("poeni", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { return@addSnapshotListener }
                if (snapshot != null) {
                    val rankingList = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(Korisnici::class.java)?.copy(id = doc.id)
                    }
                    _userRanking.value = rankingList
                }
            }
    }
}