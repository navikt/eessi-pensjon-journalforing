package no.nav.eessi.pensjon.architecture.saksflyt

/*
TODO: Legge til test av saksflyt for alle BUCer når refaktorering er ferdig.

internal class PBuc05Test : JournalforingTestBase() {

    @Test
    fun `Flere personer i SED og mangler rolle`() {
        val fnr = "12078945602"

        val sed = createSedJson(SedType.P8000, fnr, true)
        initCommonMocks(sed)

        val (journalpostSlot, journalpostResponse) = initJournalPostRequestSlot()
        val hendelse = createHendelseJson(SedType.P8000)

        listener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

        val request = journalpostSlot.captured

        // forvent tema == PEN og enhet 4303
        assertEquals("PEN", request.tema)
        assertEquals("4303", request.journalfoerendeEnhet)

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 1) { euxKlient.hentSed(any(), any()) }
    }

    @Test
    fun `Flere personer i SED, har rolle`() {
        val fnr = "12078945602"

        val sed = createSedJson(SedType.P8000, fnr, true)
        initCommonMocks(sed)

        val (journalpostSlot, journalpostResponse) = initJournalPostRequestSlot()
        val hendelse = createHendelseJson(SedType.P8000)

        listener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

        val request = journalpostSlot.captured

        // forvent tema == PEN og enhet 4303
        assertEquals("PEN", request.tema)
        assertEquals("4303", request.journalfoerendeEnhet)

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 1) { euxKlient.hentSed(any(), any()) }
    }

    @Test
    fun `Kun 1 person i SED, men fnr mangler`() {
        val sed = createSedJson(SedType.P8000)
        initCommonMocks(sed)

        every { personV3Service.hentPerson(any()) } returns Bruker()

        val (journalpost, journalpostResponse) = initJournalPostRequestSlot()

        val hendelse = createHendelseJson(SedType.P8000)

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

        listener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

        val request = journalpost.captured
        val oppgaveMelding = mapJsonToAny(meldingSlot.captured, typeRefs<OppgaveMelding>())

        assertEquals("JOURNALFORING", oppgaveMelding.oppgaveType())
        assertEquals(Enhet.ID_OG_FORDELING, oppgaveMelding.tildeltEnhetsnr)
        assertEquals(journalpostResponse.journalpostId, oppgaveMelding.journalpostId)

        // forvent tema == PEN og enhet 4303
        assertEquals("PEN", request.tema)
        assertEquals("4303", request.journalfoerendeEnhet)

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 1) { euxKlient.hentSed(any(), any()) }
    }

    @Test
    fun `Kun 1 person i SED der fnr finnes, men saksnummer mangler`() {
        initCommonMocks(createSedJson(SedType.P8000))

        every { personV3Service.hentPerson(any()) } returns Bruker()
        every {
            bestemSakOidcRestTemplate.exchange("/", HttpMethod.POST, any<HttpEntity<String>>(), any<Class<String>>())
        } returns ResponseEntity.ok().body(BestemSakResponse(null, sakInformasjonListe = emptyList()).toJson())

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

        val (journalpost, journalpostResponse) = initJournalPostRequestSlot()
        val hendelse = createHendelseJson(SedType.P8000)

        listener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

        val request = journalpost.captured

        val oppgaveMelding = mapJsonToAny(meldingSlot.captured, typeRefs<OppgaveMelding>())

        assertEquals("JOURNALFORING", oppgaveMelding.oppgaveType())
        assertEquals(Enhet.ID_OG_FORDELING, oppgaveMelding.tildeltEnhetsnr)
        assertEquals(journalpostResponse.journalpostId, oppgaveMelding.journalpostId)

        // forvent tema == PEN og enhet 4303
        assertEquals("PEN", request.tema)
        assertEquals("4303", request.journalfoerendeEnhet)

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 1) { euxKlient.hentSed(any(), any()) }
    }


    private fun initCommonMocks(sed: String) {
        every { fagmodulKlient.hentAlleDokumenter(any()) } returns getResource("fagmodul/alldocumentsids.json")
        every { euxKlient.hentSed(any(), any()) } returns sed
        every { diskresjonService.hentDiskresjonskode(any()) } returns null
        every { euxKlient.hentSedDokumenter(any(), any()) } returns getResource("pdf/pdfResponseUtenVedlegg.json")
    }

    private fun getResource(resourcePath: String): String? =
        javaClass.classLoader.getResource(resourcePath)!!.readText()
}*/
