package com.example.dailytrack_mobile.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.dailytrack_mobile.data.local.demo.DemoDataManager
import com.example.dailytrack_mobile.data.remote.api.DailyTrackApi
import com.example.dailytrack_mobile.data.remote.dto.AddFundRequestDto
import com.example.dailytrack_mobile.data.remote.dto.FundDto
import com.example.dailytrack_mobile.data.remote.dto.FundImportResponseDto
import com.example.dailytrack_mobile.data.remote.dto.FundSearchResultDto
import com.example.dailytrack_mobile.data.remote.dto.FundSipRequestDto
import com.example.dailytrack_mobile.data.remote.dto.FundsResponseDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mutual funds held outside Kite: the server values them every day at AMFI's NAV
 * (backend/funds_service.py). Each change refreshes the Investments screen.
 */
@Singleton
class FundsRepository @Inject constructor(
    private val api: DailyTrackApi,
    private val investments: InvestmentsRepository,
    private val demo: DemoDataManager,
    @param:ApplicationContext private val context: Context
) {
    /** A picked statement: what to call it on screen. */
    data class PickedFile(val uri: Uri, val name: String, val bytes: Long?)

    suspend fun funds(): Result<List<FundDto>> =
        if (demo.isDemoModeEnabled()) Result.success(emptyList()) else call { api.getFunds() }.map { it.funds }

    suspend fun search(query: String): Result<List<FundSearchResultDto>> =
        call { api.searchFunds(query) }.map { it.results }

    suspend fun add(request: AddFundRequestDto): Result<List<FundDto>> = change { api.addFund(request) }

    suspend fun remove(code: String): Result<List<FundDto>> = change { api.removeFund(code) }

    suspend fun startSip(code: String, sip: FundSipRequestDto): Result<List<FundDto>> = change { api.addFundSip(code, sip) }

    suspend fun stopSip(id: Long): Result<List<FundDto>> = change { api.stopFundSip(id) }

    fun describe(uri: Uri): PickedFile {
        var name = "statement.pdf"
        var size: Long? = null
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getString(0)?.let { name = it }
                        if (!cursor.isNull(1)) size = cursor.getLong(1)
                    }
                }
        }
        return PickedFile(uri, name, size)
    }

    /**
     * Sends the PDF and its password to be read once. Neither is kept here or
     * there, and the upload never goes to the debug log (NetworkModule).
     */
    suspend fun import(file: Uri, password: String): Result<FundImportResponseDto> {
        val bytes = withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.openInputStream(file)?.use { it.readBytes() } }.getOrNull()
        } ?: return Result.failure(Exception("Couldn't open that file"))
        if (bytes.size > MAX_STATEMENT_BYTES) return Result.failure(Exception("That PDF is too large"))
        if (!bytes.copyOfRange(0, minOf(4, bytes.size)).contentEquals("%PDF".toByteArray())) {
            return Result.failure(Exception("That isn't a PDF"))
        }
        val part = MultipartBody.Part.createFormData("file", "cas.pdf", bytes.toRequestBody("application/pdf".toMediaType()))
        val secret = password.toRequestBody("text/plain".toMediaType())
        return call { api.importFundStatement(part, secret) }.onSuccess { changed() }
    }

    private suspend fun change(block: suspend () -> Response<FundsResponseDto>): Result<List<FundDto>> =
        call(block).map { it.funds }.onSuccess { changed() }

    private fun changed() {
        investments.clearCache()
        demo.notifyDataUpdated()
    }

    private suspend fun <T> call(block: suspend () -> Response<T>): Result<T> = try {
        val response = block()
        val body = response.body()
        if (response.isSuccessful && body != null) Result.success(body)
        else Result.failure(Exception(serverMessage(response) ?: "Something went wrong (${response.code()})"))
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        Result.failure(Exception("Couldn't reach the server"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun serverMessage(response: Response<*>): String? = response.errorBody()?.string()?.let {
        runCatching { org.json.JSONObject(it).optString("message") }.getOrNull()?.takeIf { m -> m.isNotBlank() }
    }

    private companion object {
        const val MAX_STATEMENT_BYTES = 10 * 1024 * 1024
    }
}
