package de.willuhn.jameica.hbci.io; 

import java.io.InputStream; 

import java.rmi.RemoteException; 
import java.text.ParseException; 
import java.text.SimpleDateFormat; 
import java.util.Date; 

import org.mustangproject.ZUGFeRD.ZUGFeRDImporter; 

import de.willuhn.jameica.hbci.HBCI; 
import de.willuhn.jameica.hbci.Settings; 
import de.willuhn.jameica.hbci.gui.dialogs.KontoAuswahlDialog; 
import de.willuhn.jameica.hbci.gui.filter.KontoFilter; 
import de.willuhn.jameica.hbci.messaging.ImportMessage; 
import de.willuhn.jameica.hbci.rmi.AuslandsUeberweisung; 
import de.willuhn.jameica.hbci.rmi.Konto; 
import de.willuhn.jameica.hbci.server.KontoUtil; 
import de.willuhn.jameica.system.Application; 
import de.willuhn.jameica.system.BackgroundTask; 
import de.willuhn.jameica.system.OperationCanceledException; 
import de.willuhn.logging.Logger; 
import de.willuhn.util.ApplicationException; 
import de.willuhn.util.I18N; 
import de.willuhn.util.ProgressMonitor; 


import java.io.File; 
import java.io.IOException; 

import de.willuhn.jameica.hbci.rmi.AuslandsUeberweisung; 

import de.willuhn.jameica.hbci.Settings; 

import de.willuhn.jameica.hbci.rmi.Konto; 
import java.util.Date; 



public class PDFImporter implements Importer 
{ 
  private final static I18N i18n = Application.getPluginLoader().getPlugin(HBCI.class).getResources().getI18N(); 

  /** 
   * @see de.willuhn.jameica.hbci.io.IO#getName() 
   */ 
  @Override 
  public String getName() 
  { 
    return i18n.tr("ZUGFeRD PDF"); 
  } 

  /** 
   * @see de.willuhn.jameica.hbci.io.IO#getIOFormats(java.lang.Class) 
   */ 
  @Override 
  public IOFormat[] getIOFormats(Class objectType) 
  { 
    if (!AuslandsUeberweisung.class.equals(objectType)) 
      return null; // Wir bieten uns nur fuer SEPA-Ueberweisungen an 

    IOFormat f = new IOFormat() { 
      public String getName() 
      { 
        return PDFImporter.this.getName(); 
      } 

      /** 
       * @see de.willuhn.jameica.hbci.io.IOFormat#getFileExtensions() 
       */ 
      public String[] getFileExtensions() 
      { 
        return new String[] {"*.pdf"}; 
      } 
    }; 
    return new IOFormat[] { f }; 
  } 

  /** 
   * @see de.willuhn.jameica.hbci.io.Importer#doImport(java.lang.Object, de.willuhn.jameica.hbci.io.IOFormat, java.io.InputStream, de.willuhn.util.ProgressMonitor)
   */ 
  @Override 
  public void doImport(Object context, IOFormat format, InputStream is, ProgressMonitor monitor, BackgroundTask bgt) throws RemoteException, ApplicationException
  { 
    monitor.setStatusText(i18n.tr("Starte Import")); 
    monitor.addPercentComplete(10); 

    final String iban=""; 


    monitor.log(i18n.tr("Ermittle Konto anhand IBAN {0}",iban)); 

    Konto konto = KontoUtil.findByIBAN(iban); 
    if (konto == null) 
    { 
      try 
      { 
        KontoAuswahlDialog d = new KontoAuswahlDialog(null,KontoFilter.FOREIGN,KontoAuswahlDialog.POSITION_CENTER); 
        konto = (Konto) d.open(); 
      } 
      catch (OperationCanceledException oce) 
      { 
        throw oce; 
      } 
      catch (ApplicationException ae) 
      { 
        throw ae; 
      } 
      catch (Exception e) 
      { 
        Logger.error("error while choosing account",e); 
        throw new ApplicationException(i18n.tr("Konto konnte nicht ausgewählt werden: {0}",e.getMessage()),e); 
      } 
    } 
    monitor.addPercentComplete(20); 
    monitor.log(i18n.tr("Erstelle SEPA-Überweisung")); 

    AuslandsUeberweisung u = Settings.getDBService().createObject(AuslandsUeberweisung.class,null); 
     
    ZUGFeRDImporter zi=new ZUGFeRDImporter(); 
     
    try { 
    zi.extractLowLevel(is); 
    if (zi.canParse()) { 
      zi.parse(); 
       
        u.setBetrag(Double.valueOf(zi.getAmount())); 
         
        u.setGegenkontoName(zi.getHolder()); 
        u.setKonto(konto); 
        SimpleDateFormat sdf=new SimpleDateFormat("yyyyMMdd"); 
        u.setTermin(sdf.parse(zi.getDueDate())); 
        u.setZweck(zi.getForeignReference()); 

        u.setGegenkontoNummer(zi.getIBAN().replace(" ", "")); 
        u.setGegenkontoBLZ(zi.getBIC().replace(" ", "")); 

        // Speichern 
        u.store(); 

        // Per Messaging Bescheid geben, dass eine neue SEPA-Ueberweisung vorliegt. 
        // Hierbei wird die Liste der SEPA-Ueberweisungen automatisch aktualisiert. 
        Application.getMessagingFactory().sendMessage(new ImportMessage(u)); 
       
    } 
  } catch (NumberFormatException e) { 
    // TODO Auto-generated catch block 
    e.printStackTrace(); 
  } catch (IOException e) { 
    // TODO Auto-generated catch block 
    e.printStackTrace(); 
  } catch (ParseException e) { 
    // TODO Auto-generated catch block 
    e.printStackTrace(); 
  } 
  } 
} 


