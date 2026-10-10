package com.ahmad.nafs.core.di

import com.ahmad.nafs.core.letters.CachedLetterRepository
import com.ahmad.nafs.core.letters.EncryptedLetterCache
import com.ahmad.nafs.core.letters.LetterCache
import com.ahmad.nafs.core.letters.LetterRemote
import com.ahmad.nafs.core.letters.LetterRepository
import com.ahmad.nafs.core.letters.SupabaseLetterRemote
import com.ahmad.nafs.core.letters.ApiLetterReplyRemote
import com.ahmad.nafs.core.letters.LetterReplyRemote
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class LetterModule {
    @Binds abstract fun bindRepository(implementation: CachedLetterRepository): LetterRepository
    @Binds abstract fun bindRemote(implementation: SupabaseLetterRemote): LetterRemote
    @Binds abstract fun bindCache(implementation: EncryptedLetterCache): LetterCache
    @Binds abstract fun bindReplyRemote(implementation: ApiLetterReplyRemote): LetterReplyRemote
}
