package otus.homework.reactivecats

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.TimeUnit

class CatsViewModel(
    private val catsService: CatsService,
    private val localCatFactsGenerator: LocalCatFactsGenerator,
    private val context: Context
) : ViewModel() {
//
    private val _catsLiveData = MutableLiveData<Result>()
    val catsLiveData: LiveData<Result> = _catsLiveData
    private val disposable = CompositeDisposable()

    init {
        catsService.getCatFact()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnError {
                _catsLiveData.value = ServerError
            }
            .subscribe { response ->
                if (response.isSuccessful && response.body() != null) {
                    _catsLiveData.value = Success(response.body()!!)
                } else {
                    _catsLiveData.value = Error(
                        response.errorBody()?.string() ?: context.getString(
                            R.string.default_error_text
                        )
                    )
                }
            }
            .apply {
                disposable.add(this)
            }
    }

    fun getFacts() {
        Observable
            .interval(2000, TimeUnit.MILLISECONDS)
            .flatMap {
                catsService.getCatFact().toObservable().map { response ->
                    if (response.isSuccessful && response.body() != null) {
                        Success(response.body()!!)
                    } else {
                        Error(
                            response.errorBody()?.string() ?: context.getString(
                                R.string.default_error_text
                            )
                        )
                    }
                }
            }
            .onErrorResumeNext(localCatFactsGenerator.generateCatFact().map { Success(it) }.toObservable())
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { result ->
                _catsLiveData.value = result
            }.apply {
                disposable.add(this)
            }
    }

    override fun onCleared() {
        disposable.dispose()
        super.onCleared()
    }
}

class CatsViewModelFactory(
    private val catsRepository: CatsService,
    private val localCatFactsGenerator: LocalCatFactsGenerator,
    private val context: Context
) :
    ViewModelProvider.NewInstanceFactory() {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel?> create(modelClass: Class<T>): T =
        CatsViewModel(catsRepository, localCatFactsGenerator, context) as T
}

sealed class Result
data class Success(val fact: Fact) : Result()
data class Error(val message: String) : Result()
object ServerError : Result()